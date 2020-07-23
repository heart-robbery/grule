package core.module

import cn.xnatural.enet.event.EC
import cn.xnatural.enet.event.EL
import cn.xnatural.enet.event.EP
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONException
import com.alibaba.fastjson.JSONObject
import com.alibaba.fastjson.parser.Feature
import com.alibaba.fastjson.serializer.SerializerFeature
import core.module.aio.AioClient
import core.module.aio.AioServer
import core.module.aio.AioSession

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

import static core.Utils.ipv4

class Remoter extends ServerTpl {
    @Lazy def                                              sched      = bean(SchedSrv)
    // 集群的服务中心地址 [host]:port,[host1]:port2. 例 :8001 or localhost:8001
    @Lazy String                                           masterHps  = getStr('masterHps', null)
    // 集群的服务中心应用名
    @Lazy String                                           masterName = getStr('masterName', null)
    // 是否为master
    @Lazy boolean                                          master     = getBoolean('master', false)
    // 保存 app info 的属性信息
    protected final Map<String, List<Map<String, Object>>> appInfos   = new ConcurrentHashMap<>()
    /**
     * ecId -> {@link EC}
     */
    protected final Map<String, EC>                        ecMap      = new ConcurrentHashMap<>()

    protected AioClient aioClient
    protected AioServer aioServer


    Remoter() { super("remoter") }


    @EL(name = "sys.starting", async = true)
    void start() {
        if (aioClient || aioServer) throw new RuntimeException("$name is already running")
        if (sched == null) throw new RuntimeException("Need sched Server!")
        if (exec == null) exec = Executors.newFixedThreadPool(2)
        if (ep == null) {ep = new EP(exec); ep.addListenerSource(this)}

        // 如果系统中没有AioClient, 则创建
        aioClient = bean(AioClient)
        if (aioClient == null) {
            aioClient = new AioClient()
            ep.addListenerSource(aioClient); ep.fire("inject", aioClient)
            exposeBean(aioClient)
        }

        // 如果系统中没有AioServer, 则创建
        aioServer = bean(AioServer)
        if (aioServer == null) {
            aioServer = new AioServer()
            ep.addListenerSource(aioServer); ep.fire("inject", aioServer)
            exposeBean(aioServer)
            aioServer.start()
        }
        aioServer.msgFn {msg, se -> receiveMsg(msg, se)}
        aioClient.msgFn {msg, se -> receiveReply(msg, se)}

        ep.fire("${name}.started")
    }


    @EL(name = "sys.stopping", async = false)
    void stop() {
        localBean(AioClient)?.stop()
        localBean(AioServer)?.stop()
    }


    @EL(name = 'sys.started', async = true)
    protected void started() {
        queue('appUp') { appUp(appInfo, null) }
        sync(true)
    }


    /**
     * 同步远程事件调用
     * @param appName 应用名
     * @param eName 远程应用中的事件名
     * @param remoteMethodArgs 远程事件监听方法的参数
     * @return 事件执行结果值
     */
    def fire(String appName, String eName, List remoteMethodArgs) {
        final def latch = new CountDownLatch(1)
        def ec = EC.of(this).args(appName, eName, remoteMethodArgs).completeFn({latch.countDown()})
        ep.fire("remote", ec)
        latch.await()
        if (ec.success) return ec.result
        else throw new Exception(ec.failDesc())
    }


    /**
     * 异步远程事件调用
     * @param appName 应用名
     * @param eName 远程应用中的事件名
     * @param callback 回调函数. 1. 正常结果, 2. Exception
     * @param remoteMethodArgs 远程事件监听方法的参数
     */
    void fireAsync(String appName, String eName, Consumer callback, List remoteMethodArgs) {
        ep.fire("remote", EC.of(this).args(appName, eName, remoteMethodArgs).completeFn({ ec ->
            if (ec.success) callback.accept(ec.result)
            else callback.accept(new Exception(ec.failDesc()))
        }))
    }


    /**
     * 发送消息
     * @param appName 应用名
     * @param msg 消息内容
     * @param target 可用值: 'any', 'all'
     */
    Remoter sendMsg(String appName, String msg, String target = 'any') {
        def ls = appInfos.get(appName)
        assert ls : "Not found app '$appName' system online"

        final def doSend = {Map<String, Object> appInfo ->
            if (appInfo['id'] == app.id) return false

            String[] arr = appInfo.get('tcp').split(":")
            aioClient.send(arr[0], Integer.valueOf(arr[1]), msg)
            return true
        }

        if ('any' == target) {
            ls = ls.findAll {it['id'] != app.id}
            doSend(ls.get(new Random().nextInt(ls.size())))
        } else if ('all' == target) {
            ls.each {doSend(it)}
        } else {
            throw new IllegalArgumentException("Not support target '$target'")
        }
        this
    }


    /**
     * 集群应用同步函数
     * @param next 是否触发下一次自动同步
     */
    void sync(boolean next = false) {
        try {
            if (!masterHps && !masterName) {
                log.warn("'masterHps' or 'masterName' must config one"); return
            }
            doSyncFn.run()
            if (next) {
                // 下次触发策略, upInterval master越多可设置时间越长
                if (System.currentTimeMillis() - app.startup.time > 60 * 1000L) {
                    sched.after(Duration.ofSeconds(getInteger('upInterval', 120) + new Random().nextInt(90)), {sync(true)})
                } else {
                    sched.after(Duration.ofSeconds(new Random().nextInt(10) + 10), {sync(true)})
                }
            }
        } catch (ex) {
            if (next) sched.after(Duration.ofSeconds(getInteger('upInterval', 30) + new Random().nextInt(60)), {sync(true)})
            log.error("App Up error. " + (ex.message?:ex.class.simpleName), ex)
        }
    }


    /**
     * 调用远程事件
     * 执行流程: 1. 客户端发送事件:  {@link #doFire(EC, String, String, List)}
     *          2. 服务端接收到事件: {@link #receiveEventReq(com.alibaba.fastjson.JSONObject, core.module.aio.AioSession)}
     *          3. 客户端接收到返回: {@link #receiveEventResp(com.alibaba.fastjson.JSONObject)}
     * @param ec
     * @param appName 应用名
     * @param eName 远程应用中的事件名
     * @param remoteMethodArgs 远程事件监听方法的参数
     */
    @EL(name = "remote", async = false)
    protected void doFire(EC ec, String appName, String eName, List remoteMethodArgs) {
        if (aioClient == null) throw new RuntimeException("$aioClient.name not is running")
        if (app.name == null) throw new IllegalArgumentException("app.name is empty")
        ec.suspend()
        JSONObject params = new JSONObject(5, true)
        try {
            boolean trace = (ec.track || getBoolean("trace_*_*") || getBoolean("trace_*_${eName}") || getBoolean("trace_${appName}_*") || getBoolean("trace_${appName}_${eName}"))
            if (ec.id() == null) {ec.id(UUID.randomUUID().toString().replaceAll("-", ''))}
            params.put("id", ec.id())
            boolean reply = (ec.completeFn() != null) // 是否需要远程响应执行结果(有完成回调函数就需要远程响应调用结果)
            params.put("reply", reply)
            params.put("name", eName)
            // params.put("trace", trace)
            if (remoteMethodArgs) {
                JSONArray args = new JSONArray(remoteMethodArgs.size())
                params.put("args", args)
                for (Object arg : remoteMethodArgs) {
                    if (arg == null) args.add(new JSONObject(0))
                    else args.add(new JSONObject(2).fluentPut("type", arg.getClass().getName()).fluentPut("value", arg))
                }
            }
            if (reply) {
                ecMap.put(ec.id(), ec)
                // 数据发送成功. 如果需要响应, 则添加等待响应超时处理
                sched.after(Duration.ofSeconds(
                    ec.getAttr("timeout", Integer, getInteger("timeout_$eName", getInteger("eventTimeout", 20)))
                ), {
                    ecMap.remove(ec.id())?.errMsg("'${appName}_$eName' Timeout")?.resume()?.tryFinish()
                })
                if (trace) {
                    def fn = ec.completeFn()
                    ec.completeFn({
                        if (ec.success) {
                            log.info("End remote event. id: "+ ec.id() + ", result: $ec.result")
                        }
                        fn.accept(ec)
                    })
                }
            }

            // 发送请求给远程应用appName执行. 消息类型为: 'event'
            JSONObject data = new JSONObject(3)
                .fluentPut("type", "event")
                .fluentPut("source", new JSONObject(2).fluentPut('name', app.name).fluentPut('id', app.id))
                .fluentPut("data", params)
            if (ec.getAttr('toAll', Boolean.class, false)) {
                if (trace) {log.info("Fire Remote Event(toAll). app:"+ appName +", params: " + params)}
                sendMsg(appName, data.toString(), 'all')
            } else {
                if (trace) {log.info("Fire Remote Event(toAny). app:"+ appName +", params: " + params)}
                sendMsg(appName, data.toString(), 'any')
            }
        } catch (Throwable ex) {
            log.error("Error fire remote event to '" +appName+ "'. params: " + params, ex)
            ecMap.remove(ec.id())
            ec.ex(ex).resume().tryFinish()
        }
    }


    /**
     * 接收远程发送的消息
     * @param msg 消息内容
     * @param se AioSession
     */
    protected void receiveMsg(final String msg, final AioSession se) {
        JSONObject msgJo = null
        try {
            msgJo = JSON.parseObject(msg, Feature.OrderedField)
        } catch (JSONException ex) { // 不处理非json格式的消息
            return
        }

        String t = msgJo.getString("type")
        if ("event" == t) { // 远程事件请求
            def sJo = msgJo.getJSONObject('source')
            if (!sJo) {
                log.warn("Unknown source. origin data: " + msg); return
            }
            if (sJo['id'] == app.id) {
                log.warn("Not allow fire remote event to self")
                se.close()
                return
            }
            receiveEventReq(msgJo.getJSONObject("data"), se)
        } else if ("appUp" == t) { // 应用注册在线通知
            def sJo = msgJo.getJSONObject('source')
            if (!sJo) {
                log.warn("Unknown source. origin data: " + msg); return
            }
            if (sJo['id'] == app.id) {
                log.warn("Not allow register up to self")
                se.close()
                return
            }
            queue('appUp') {
                JSONObject d
                try { d = msgJo.getJSONObject("data"); appUp(d, se) }
                catch (Exception ex) {
                    log.error("Register up error!. data: " + d, ex)
                }
            }
        } else if ("cmd-log" == t) { // telnet 命令行设置日志等级
            // telnet localhost 8001
            // 例: {"type":"cmd-log", "data": "core.module.remote: debug"}$_$
            String[] arr = msgJo.getString("data").split(":")
            // Log.setLevel(arr[0].trim(), arr[1].trim())
            se.send("set log level success")
        } else if (t && t.startsWith("ls ")) {
            // {"type":"ls apps"}$_$
            def arr = t.split(" ")
            if (arr?[1] = "apps") {
                se.send(JSON.toJSONString(appInfos))
            } else if (arr?[1] == 'app' && arr?[2]) {
                se.send(JSON.toJSONString(appInfos[arr?[2]]))
            }
        } else {
            log.error("Not support exchange data type '{}'", t)
            se.close()
        }
    }


    /**
     * 接收远程回响的消息
     * @param reply 回应消息
     * @param se AioSession
     */
    protected void receiveReply(final String reply, final AioSession se) {
        log.trace("Receive reply from '{}': {}", se.sc.remoteAddress, reply)
        def jo = JSON.parseObject(reply, Feature.OrderedField)
        if ("updateAppInfo" == jo['type']) {
            updateAppInfo(jo.getJSONObject("data"))
        }
        else if ("event"== jo['type']) {
            receiveEventResp(jo.getJSONObject("data"))
        }
    }


    /**
     * 接收远程事件返回的数据. 和 {@link #doFire(EC, String, String, List)} 对应
     * @param data
     */
    protected void receiveEventResp(JSONObject data) {
        log.debug("Receive event response: {}", data)
        EC ec = ecMap.remove(data.getString("id"))
        if (ec != null) ec.errMsg(data.getString("exMsg")).result(data["result"]).resume().tryFinish()
    }


    /**
     * 接收远程事件的执行请求
     * @param data 数据
     * @param se AioSession
     */
    protected void receiveEventReq(final JSONObject data, final AioSession se) {
        log.debug("Receive event request: {}", data)
        boolean fReply = (Boolean.TRUE == data.getBoolean("reply")) // 是否需要响应
        try {
            String eId = data.getString("id")
            String eName = data.getString("name")

            EC ec = new EC()
            ec.id(eId)
            ec.args(data.getJSONArray("args") == null ? null : data.getJSONArray("args").stream().map{JSONObject jo ->
                String t = jo.getString("type")
                if (jo.isEmpty()) return null // 参数为null
                else if (String.class.name == t) return jo.getString("value")
                else if (Boolean.class.name == t) return jo.getBoolean("value")
                else if (Integer.class.name == t) return jo.getInteger("value")
                else if (Long.class.name == t) return jo.getLong("value")
                else if (Double.class.name == t) return jo.getDouble("value")
                else if (Short.class.name == t) return jo.getShort("value")
                else if (Float.class.name == t) return jo.getFloat("value")
                else if (BigDecimal.class.name == t) return jo.getBigDecimal("value")
                else if (JSONObject.class.name == t || Map.class.name == t) return jo.getJSONObject("value")
                else if (JSONArray.class.name == t || List.class.name == t) return jo.getJSONArray("value")
                else throw new IllegalArgumentException("Not support parameter type '" + t + "'")
            }.toArray())

            if (fReply) {
                ec.completeFn(ec1 -> {
                    JSONObject r = new JSONObject(3)
                    r.put("id", ec.id())
                    if (!ec.success) { r.put("exMsg", ec.failDesc()) }
                    r.put("result", ec.result)
                    se.send(JSON.toJSONString(
                        new JSONObject(3)
                            .fluentPut("type", "event")
                            .fluentPut("source", new JSONObject(2).fluentPut('name', app.name).fluentPut('id', app.id))
                            .fluentPut("data", r),
                        SerializerFeature.WriteMapNullValue
                    ))
                })
            }
            ep.fire(eName, ec.sync()) // 同步执行, 没必要异步去切换线程
        } catch (ex) {
            log.error("invoke event error. data: " + data, ex)
            if (fReply) {
                JSONObject r = new JSONObject(3)
                r.put("id", data.getString("id"))
                r.put("result", null)
                r.put("exMsg", ex.message ? ex.message: ex.getClass().name)
                def res = new JSONObject(3)
                    .fluentPut("type", "event")
                    .fluentPut("source", new JSONObject(2).fluentPut('name', app.name).fluentPut('id', app.id))
                    .fluentPut("data", r)
                se.send(JSON.toJSONString(res, SerializerFeature.WriteMapNullValue))
            }
        }
    }


    /**
     * 应用上线通知
     * NOTE: 此方法线程已安全
     * 例: {"name": "应用名", "id": "应用实例id", "tcp":"localhost:8001", "http":"localhost:8080", "udp": "localhost:11111"}
     * id 和 tcp 必须唯一
     * @param data
     * @param se
     */
    protected void appUp(final Map data, final AioSession se) {
        if (!data || !data['name'] || !data['tcp'] || !data['id']) { // 数据验证
            log.warn("App up data incomplete: " + data)
            return
        }
        log.debug("Receive app up: {}", data)
        data["_uptime"] = System.currentTimeMillis()

        //1. 先删除之前的数据,再添加新的
        boolean isNew = true
        final List<Map<String, Object>> apps = appInfos.computeIfAbsent(data["name"], {new LinkedList<>()})
        for (final def it = apps.iterator(); it.hasNext(); ) {
            if (it.next()["id"] == data["id"]) {it.remove(); isNew = false; break}
        }
        apps << data
        if (isNew && data['id'] != app.id) log.info("New app '{}' online. {}", data["name"], data)
        // if (data['id'] != app.id) ep.fire("updateAppInfo", data) // 同步信息给本服务器的tcp-client

        //2. 遍历所有的数据,删除不必要的数据, 同步注册信息
        for (final def it = appInfos.entrySet().iterator(); it.hasNext(); ) {
            def e = it.next()
            for (final def it2 = e.value.iterator(); it2.hasNext(); ) {
                final def cur = it2.next()
                // 删除空的坏数据
                if (!cur) {it2.remove(); continue}
                // 删除和当前up的app 相同的tcp的节点(节点重启,但节点上次的信息还没被移除)
                if (data['id'] != cur['id'] && data['tcp'] && data['tcp'] == cur['tcp']) {
                    it2.remove()
                    log.info("Drop same tcp node: {}", cur)
                    continue
                }
                // 删除一段时间未活动的注册信息, dropAppTimeout 单位: 分钟
                if ((System.currentTimeMillis() - (Long) cur.getOrDefault("_uptime", System.currentTimeMillis()) > getInteger("dropAppTimeout", 15) * 60 * 1000)  && cur["id"] != app.id) {
                    it2.remove()
                    log.warn("Drop timeout node: {}", cur)
                    continue
                }
                // 更新当前App up的时间
                if (cur["id"] == app.id && (System.currentTimeMillis() - cur['_uptime'] > 1000 * 60)) { // 超过1分钟更新一次,不必每次更新
                    cur['_uptime'] = System.currentTimeMillis()
                    def self = appInfo // 判断当前机器ip是否变化
                    if (self && self['tcp'] != cur['tcp']) cur.putAll(self)
                }
                // 返回所有的注册信息给当前来注册的客户端
                if (cur["id"] != data["id"]) {
                    se?.send(new JSONObject(2).fluentPut("type", "updateAppInfo").fluentPut("data", cur).toString())
                }
            }
            // 删除没有对应的服务信息的应用
            if (!e.value) {it.remove(); continue}
            // 如果是新系统上线, 则主动通知其它系统
            if (isNew && data['id'] != app.id && e.value?.size() > 0) {
                ep.fire("remote", EC.of(this).async(true).attr('toAll', true).args(e.key, "updateAppInfo", [data]))
            }
        }
    }


    /**
     * 更新 app 信息
     * @param data app node信息
     *  例: {"name":"rc", "id":"rc_b70d18d52269451291ea6380325e2a84", "tcp":"192.168.56.1:8001","http":"localhost:8000"}
     *  属性不为空: name, id, tcp
     */
    @EL(name = "updateAppInfo", async = false)
    void updateAppInfo(final JSONObject data) {
        if (!data || !data["name"] || !data['id'] || !data['tcp']) {
            log.warn("App up data incomplete: " + data)
            return
        }
        log.trace("Update app info: {}", data)
        if (app.id == data["id"] || appInfo?['tcp'] == data['tcp']) return // 不把系统本身的信息放进去

        queue('appUp') {
            boolean add = true
            boolean isNew = true
            final def apps = appInfos.computeIfAbsent(data["name"], {new LinkedList<>()})
            for (final def itt = apps.iterator(); itt.hasNext(); ) {
                final def e = itt.next()
                if (e["id"] == data["id"]) {
                    isNew = false
                    if (data['_uptime'] > e['_uptime']) {
                        itt.remove()
                    } else {
                        add = false
                    }
                    break
                }
            }
            if (add) {
                apps << data
                if (isNew) log.info("New Node added. Node: '{}'", data)
                else log.trace("Update app info: {}", data)
            }
        }
    }


    /**
     * 向master同步函数
     */
    @Lazy protected def doSyncFn = new Runnable() {
        def hps = masterHps?.split(",").collect {
            if (!it) return null
            try {
                def arr = it.split(":")
                return Tuple.tuple(arr[0].trim()?:'127.0.0.1', Integer.valueOf(arr[1].trim()))
            } catch (ex) {
                log.error("'masterHps' config error. " + it, ex)
            }
            null
        }.findAll {it && it.v1 && it.v2}
        @Lazy Set<String> localHps = { //忽略的hp
            Set<String> r = new HashSet<>()
            def port = aioServer.hp?.split(":")[1]
            if (port) {
                r.add('localhost:' + port)
                r.add('127.0.0.1:' + port)
                r.add(ipv4()+ ":" +port)
            }
            def exposeTcp = getStr('exposeTcp', null)
            if (exposeTcp) r.add(exposeTcp)
            return r
        }()
        // 上传的数据格式
        @Lazy def dataFn = {
            def info = appInfo
            if (!info) return null
            new JSONObject(3).fluentPut("type", 'appUp')
                .fluentPut("source", new JSONObject(2).fluentPut('name', app.name).fluentPut('id', app.id))
                .fluentPut("data", info)
                .toString()
        }
        // 用 hps 函数
        @Lazy def sendToHps = { String data->
            // 如果是域名,得到所有Ip, 除本机
            List<Tuple2<String, Integer>> ls = hps.collectMany {hp ->
                InetAddress.getAllByName(hp.v1)
                    .collect {addr ->
                        if (localHps.contains(addr.hostAddress+ ":" +hp.v2)) return null
                        else return Tuple.tuple(addr.hostAddress, hp.v2)
                    }
                    .findAll {it}
            }

            if (master) { // 如果是master, 则同步所有
                ls.each {hp -> aioClient.send(hp.v1, hp.v2, data)}
            } else {
                def hp = ls.get(new Random().nextInt(ls.size()))
                aioClient.send(hp.v1, hp.v2, data)
            }
        }
        final def running = new AtomicBoolean(false)

        @Override
        void run() {
            try {
                if (!hps && !masterName) {
                    log.error("'masterHps' or 'masterName' must config one"); return
                }
                if (!running.compareAndSet(false, true)) return
                String data = dataFn()
                if (!data) return

                try {
                    sendMsg(masterName, data, (master ? 'all' : 'any'))
                } catch (e) {
                    sendToHps(data)
                }
                log.debug("App Up success. {}", data)
            } finally {
                running.set(false)
            }
        }
    }


    /**
     * 应用自己的信息
     * 例: {"id":"rc_GRLD5JhT4g", "name":"rc", "tcp":"192.168.2.104:8001", "http":"192.168.2.104:8000", "master": true}
     */
    Map getAppInfo() {
        final Map info = new LinkedHashMap(5)
        info.put("id", app.id)
        info.put("name", app.name)

        // http
        String http = getStr('exposeHttp', null) // 配置暴露的http
        info.put("http", http ?: ep.fire("http.hp"))

        // tcp
        String tcp = getStr('exposeTcp', null)
        info.put('tcp', tcp ?: ((aioServer?.hp?.split(":")?[0]) ? aioServer.hp : {
            def ip = ipv4()
            if (ip) return ip + aioServer?.hp
            else {
                log.error("Can't get local ipv4")
            }
            null
        }()))

        info.put('master', master)
        return info
    }
}

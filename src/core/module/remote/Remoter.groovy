package core.module.remote

import cn.xnatural.enet.event.EC
import cn.xnatural.enet.event.EL
import cn.xnatural.enet.event.EP
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.alibaba.fastjson.serializer.SerializerFeature
import core.module.SchedSrv
import core.module.ServerTpl

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

import static core.Utils.ipv4

class Remoter extends ServerTpl {
    @Lazy def                 sched      = bean(SchedSrv)
    /**
     * ecId -> EC
     */
    @Lazy Map<String, EC>     ecMap      = new ConcurrentHashMap<>()
    // 集群的服务中心地址 [host]:port,[host1]:port2. 例 :8001 or localhost:8001
    @Lazy String              masterHps  = getStr('masterHps', null)
    // 集群的服务中心应用名
    @Lazy String              masterName = getStr('masterName', null)
    // 是否为master
    @Lazy boolean             master     = getBoolean('master', false)
    // 设置默认tcp折包粘包的分割
    @Lazy String              delimiter  = getStr('delimiter', '$_$')
    protected       TCPClient tcpClient
    protected       TCPServer tcpServer


    Remoter() { super("remoter") }


    @EL(name = "sys.starting", async = true)
    def start() {
        if (tcpClient || tcpServer) throw new RuntimeException("$name is already running")
        if (sched == null) throw new RuntimeException("Need sched Server!")
        if (exec == null) exec = Executors.newFixedThreadPool(2)
        if (ep == null) {ep = new EP(exec); ep.addListenerSource(this)}

        // 如果系统中没有TCPClient, 则创建
        tcpClient = bean(TCPClient)
        if (tcpClient == null) {
            tcpClient = new TCPClient()
            ep.addListenerSource(tcpClient); ep.fire("inject", tcpClient)
            tcpClient.attr("delimiter", delimiter)
            exposeBean(tcpClient)
            tcpClient.start()
        }

        // 如果系统中没有TCPServer, 则创建
        tcpServer = bean(TCPServer)
        if (tcpServer == null) {
            tcpServer = new TCPServer()
            ep.addListenerSource(tcpServer); ep.fire("inject", tcpServer)
            tcpServer.attr("delimiter", delimiter)
            exposeBean(tcpServer)
            tcpServer.start()
        }

        ep.fire("${name}.started")
    }


    @EL(name = "sys.stopping", async = false)
    def stop() {
        localBean(TCPClient)?.stop()
        localBean(TCPServer)?.stop()
    }


    @EL(name = 'sys.started', async = true)
    protected started() {
        queue('appUp') { tcpServer.appUp(selfInfo, null) }
        sync(true)
    }


    /**
     * 调用远程事件
     * 执行流程: 1. 客户端发送事件:  {@link #sendEvent(EC, String, String, List)}
     *          2. 服务端接收到事件: {@link #receiveEventReq(com.alibaba.fastjson.JSONObject, java.util.function.Consumer)}
     *          3. 客户端接收到返回: {@link #receiveEventResp(com.alibaba.fastjson.JSONObject)}
     * @param ec
     * @param appName 应用名
     * @param eName 远程应用中的事件名
     * @param remoteMethodArgs 远程事件监听方法的参数
     */
    @EL(name = "remote", async = false)
    protected sendEvent(EC ec, String appName, String eName, List remoteMethodArgs) {
        if (tcpClient == null) throw new RuntimeException("$tcpClient.name not is running")
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
                sched.after(Duration.ofSeconds(ec.getAttr("timeout", Integer, getInteger("timeout_$eName", getInteger("eventTimeout", 20)))), {
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
                if (trace) {log.info("Start Remote Event(toAll). app:"+ appName +", params: " + params)}
                tcpClient.send(appName, data.toString(), 'all')
            } else {
                if (trace) {log.info("Start Remote Event(toAny). app:"+ appName +", params: " + params)}
                tcpClient.send(appName, data.toString(), 'any')
            }
        } catch (Throwable ex) {
            log.error("Error fire remote event to '" +appName+ "'. params: " + params, ex)
            ecMap.remove(ec.id())
            ec.ex(ex).resume().tryFinish()
        }
    }


    /**
     * 同步远程事件调用
     * @param appName 应用名
     * @param eName 远程应用中的事件名
     * @param remoteMethodArgs 远程事件监听方法的参数
     * @return
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
    def fireAsync(String appName, String eName, Consumer callback, List remoteMethodArgs) {
        ep.fire("remote", EC.of(this).args(appName, eName, remoteMethodArgs).completeFn({ec ->
            if (ec.success) callback.accept(ec.result)
            else callback.accept(new Exception(ec.failDesc()))
        }))
    }


    /**
     * 接收远程事件返回的数据. 和 {@link #sendEvent(EC, String, String, List)} 对应
     * @param data
     */
    def receiveEventResp(JSONObject data) {
        log.debug("Receive event response: {}", data)
        EC ec = ecMap.remove(data.getString("id"))
        if (ec != null) ec.errMsg(data.getString("exMsg")).result(data["result"]).resume().tryFinish()
    }


    /**
     * 接收远程事件的执行请求
     * @param data 数据
     * @param reply 响应回调(传参为响应的数据)
     */
    def receiveEventReq(JSONObject data, Consumer<String> reply) {
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
                    reply.accept(JSON.toJSONString(
                        new JSONObject(3)
                            .fluentPut("type", "event")
                            .fluentPut("source", new JSONObject(2).fluentPut('name', app.name).fluentPut('id', app.id))
                            .fluentPut("data", r),
                        SerializerFeature.WriteMapNullValue
                    ))
                })
            }
            ep.fire(eName, ec.sync()) // 同步执行, 没必要异步去切换线程
        } catch (Throwable ex) {
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
                reply.accept(JSON.toJSONString(res, SerializerFeature.WriteMapNullValue))
            }
        }
    }


    /**
     * 同步函数
     * @param next 是否触发下一次
     */
    def sync(boolean next = false) {
        try {
            syncFn.run()
            if (next) {
                // 下次触发策略, upInterval master越多可设置时间越长
                if (System.currentTimeMillis() - app.startup.time > 60 * 1000L) {
                    int upInterval = getInteger('upInterval', (master ? Math.min(180, tcpClient.apps.get(app.name)?.nodes?.size()?:1 * 40) : 90))
                    sched.after(Duration.ofSeconds(upInterval + new Random().nextInt(90)), {sync(true)})
                } else {
                    sched.after(Duration.ofSeconds(10), {sync(true)})
                }
            }
        } catch (Throwable ex) {
            if (next) sched.after(Duration.ofSeconds(getInteger('upInterval', 30) + new Random().nextInt(60)), {sync(true)})
            log.error("Up error. " + (ex.message?:ex.class.simpleName))
        }
    }


    /**
     * 向master同步函数
     */
    @Lazy protected def syncFn = new Runnable() {
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
            def port = tcpServer.hp?.split(":")[1]
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
            new JSONObject(3).fluentPut("type", 'appUp')
                .fluentPut("source", new JSONObject(2).fluentPut('name', app.name).fluentPut('id', app.id))
                .fluentPut("data", selfInfo)
                .toString()
        }
        // 用 hps 函数
        @Lazy def toHps = { String data->
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
                ls.each {hp -> tcpClient.send(hp.v1, hp.v2, data)}
            } else {
                def hp = ls.get(new Random().nextInt(ls.size()))
                tcpClient.send(hp.v1, hp.v2, data)
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

                try {
                    tcpClient.send(masterName, data, (master ? 'all' : 'any'))
                } catch (e) {
                    toHps(data)
                }
                log.debug("Up success. {}", data)
            } finally {
                running.set(false)
            }
        }
    }


    /**
     * 自己的信息
     * 例: {"id":"rc_b70d18d52269451291ea6380325e2a84", "name":"rc", "tcp":"192.168.56.1:8001", "http":"localhost:8000"}
     */
    JSONObject getSelfInfo() {
        JSONObject data = new JSONObject(5)
        if (app.id && app.name) {
            data.put("id", app.id)
            data.put("name", app.name)
        } else {
            throw new IllegalArgumentException("Current App name or id is empty")
        }

        // http
        def exposeHttp = getStr('exposeHttp', null) // 配置暴露的http
        if (exposeHttp) data.put("http", exposeHttp)
        else Optional.ofNullable(ep.fire("http.hp")).ifPresent{ data.put("http", it) }

        // tcp
        def exposeTcp = getStr('exposeTcp', null) // 配置暴露的tcp
        if (exposeTcp) data.put("tcp", exposeTcp)
        else if (tcpServer?.hp?.split(":")?[0]) data.put("tcp", tcpServer.hp)
        else {
            def ip = ipv4()
            if (ip) {
                data.put("tcp", ip + tcpServer?.hp)
            } else {
                throw new IllegalArgumentException("Can't get local ipv4")
            }
        }
        data.put('master', master)

        return data
    }
}

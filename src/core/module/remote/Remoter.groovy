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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

import static core.Utils.getLog
import static core.Utils.ipv4

class Remoter extends ServerTpl {
    @Lazy def                 sched     = bean(SchedSrv)
    /**
     * ecId -> EC
     */
    @Lazy Map<String, EC>     ecMap     = new ConcurrentHashMap<>()
    protected       TCPClient tcpClient
    protected       TCPServer tcpServer
    // 集群的服务中心地址 [host]:port,[host1]:port2. 例 :8001 or localhost:8001
    @Lazy String              masterHps = getStr('masterHps', null)
    // 集群的服务中心应用名
    @Lazy String              master    = getStr('master', null)
    // 设置默认tcp折包粘包的分割
    @Lazy String              delimiter = getStr('delimiter', '$_$')


    Remoter() { super("remoter") }


    @EL(name = "sys.starting")
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


    @EL(name = 'sys.started')
    protected started() {
        queue('appUp') { tcpServer.appUp(selfInfo, null) }
        syncFn.run()
    }


    /**
     * 调用远程事件
     * 执行流程: 1. 客户端发送事件:  {@link #sendEvent(EC, String, String, Object[])}
     *          2. 服务端接收到事件: {@link #receiveEventReq(com.alibaba.fastjson.JSONObject, java.util.function.Consumer)}
     *          3. 客户端接收到返回: {@link #receiveEventResp(com.alibaba.fastjson.JSONObject)}
     * @param ec
     * @param appName 应用名
     * @param eName 远程应用中的事件名
     * @param remoteMethodArgs 远程事件监听方法的参数
     */
    @EL(name = "remote")
    protected sendEvent(EC ec, String appName, String eName, Object[] remoteMethodArgs) {
        if (tcpClient == null) throw new RuntimeException("$tcpClient.name not is running")
        if (app.name == null) throw new IllegalArgumentException("app.name is empty")
        ec.suspend()
        JSONObject params = new JSONObject(4)
        try {
            if (ec.id() == null) {ec.id(UUID.randomUUID().toString().replaceAll("-", ''))}
            params.put("eId", ec.id())
            boolean reply = (ec.completeFn() != null) // 是否需要远程响应执行结果(有完成回调函数就需要远程响应调用结果)
            params.put("reply", reply)
            params.put("eName", eName)
            if (remoteMethodArgs != null) {
                JSONArray args = new JSONArray(remoteMethodArgs.length)
                params.put("args", args)
                for (Object arg : remoteMethodArgs) {
                    if (arg == null) args.add(new JSONObject(0))
                    else args.add(new JSONObject(2).fluentPut("type", arg.getClass().getName()).fluentPut("value", arg))
                }
            }
            if (reply) ecMap.put(ec.id(), ec)

            // 发送请求给远程应用appName执行. 消息类型为: 'event'
            JSONObject data = new JSONObject(3)
                    .fluentPut("type", "event")
                    .fluentPut("source", new JSONObject(2).fluentPut('name', app.name).fluentPut('id', app.id))
                    .fluentPut("data", params)
            if (ec.getAttr('toAll', Boolean.class, false)) {
                tcpClient.send(appName, data.toString(), 'all')
            } else {
                // 默认 toAny
                tcpClient.send(appName, data.toString())
            }
            if (reply) { // 数据发送成功. 如果需要响应, 则添加等待响应超时处理
                sched.after(Duration.ofSeconds(getInteger("eventTimeout.$eName", getInteger("eventTimeout", 20))), {
                    ecMap.remove(ec.id())?.errMsg("'$eName' Timeout")?.resume()?.tryFinish()
                })
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
     */
    def event(String appName, String eName, Object... remoteMethodArgs) {
//        def ec = EC.of(this).completeFn({ec ->
//            ec.notify()
//        })
//        sendEvent(ec, appName, eName, remoteMethodArgs)
//        ec.wait()
    }


    /**
     * 接收远程事件返回的数据. 和 {@link #sendEvent(EC, String, String, Object[])} 对应
     * @param data
     */
    def receiveEventResp(JSONObject data) {
        log.debug("Receive event response: {}", data)
        EC ec = ecMap.remove(data.getString("eId"))
        if (ec != null) ec.errMsg(data.getString("exMsg")).result(data["result"]).resume().tryFinish()
    }


    /**
     * 接收远程事件的执行请求
     * @param data 数据
     * @param reply 响应回调(传参为响应的数据)
     */
    def receiveEventReq(JSONObject data, Consumer<String> reply) {
        log.debug("Receive event request: {}", data);
        boolean fReply = (Boolean.TRUE == data.getBoolean("reply")) // 是否需要响应
        try {
            String eId = data.getString("eId")
            String eName = data.getString("eName")

            EC ec = new EC()
            ec.id(eId)
            ec.args(data.getJSONArray("args") == null ? null : data.getJSONArray("args").stream().map{JSONObject jo ->
                String t = jo.getString("type")
                if (jo.isEmpty()) return null // 参数为null
                else if (String.class.name == t) return jo.getString("value")
                else if (Boolean.class.name == t) return jo.getBoolean("value")
                else if (Integer.class.name == t) return jo.getInteger("value")
                else if (Short.class.name == t) return jo.getShort("value")
                else if (Long.class.name == t) return jo.getLong("value")
                else if (Double.class.name == t) return jo.getDouble("value")
                else if (Float.class.name == t) return jo.getFloat("value")
                else if (BigDecimal.class.name == t) return jo.getBigDecimal("value")
                else if (JSONObject.class.name == t || Map.class.name == t) return jo.getJSONObject("value")
                else if (JSONArray.class.name == t || List.class.name == t) return jo.getJSONArray("value")
                else throw new IllegalArgumentException("Not support parameter type '" + t + "'")
            }.toArray())

            if (fReply) {
                ec.completeFn(ec1 -> {
                    JSONObject r = new JSONObject(3)
                    r.put("eId", ec.id())
                    if (!ec.isSuccess()) { r.put("exMsg", ec.failDesc()); }
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
            ep.fire(eName, ec)
        } catch (Throwable ex) {
            log.error("invoke event error. data: " + data, ex)
            if (fReply) {
                JSONObject r = new JSONObject(4)
                r.put("eId", data.getString("eId"))
                r.put("success", false)
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


    // 向master同步函数
    @Lazy def syncFn = new Runnable() {
        private final def running = new AtomicBoolean(false)
        private final def toAll = getBoolean('syncToAll', false)
        private final def hps = masterHps?.split(",").collect {
            def arr = it.split(":")
            Tuple.tuple(arr[0].trim()?:'127.0.0.1', Integer.valueOf(arr[1].trim()))
        }.findAll {it.v1 && it.v2}
        private final Set<String> localHps = { //忽略的hp
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

        @Override
        void run() {
            try {
                if (!running.compareAndSet(false, true)) return //同时只允许一个线程执行
                if (!hps && !master) {
                    log.error("'masterHps' or 'master' must config one"); return
                }
                def info = selfInfo
                if (!info) return

                // 上传的数据格式
                JSONObject data = new JSONObject(3)
                data.put("type", 'appUp') // 数据类型 appUp
                data.put("source", new JSONObject(2).fluentPut('name', app.name).fluentPut('id', app.id)) // 表明来源
                data.put("data", info)

                // 用 hps 函数
                def fn = {
                    if (toAll) {
                        hps.each {hp ->
                            InetAddress.getAllByName(hp.v1).each {addr-> // 如果是域名,得到所有Ip
                                if (!localHps.contains(addr.hostAddress+ ":" +hp.v2)) { // 除本机
                                    tcpClient.send(addr.hostAddress, hp.v2, data.toString())
                                }
                            }
                        }
                    } else {
                        def hp = hps.get(new Random().nextInt(hps.size()))
                        tcpClient.send(hp.v1, hp.v2, data.toString())
                    }
                }

                try {
                    tcpClient.send(master, data.toString(), (toAll ? 'all' : 'any'))
                } catch (e) {
                    fn()
                }

                log.debug("Register up success. {}", data)
                sched.after(Duration.ofSeconds(getInteger('upInterval', 90) + new Random().nextInt(60)), syncFn)
            } catch (Throwable ex) {
                sched.after(Duration.ofSeconds(getInteger('upInterval', 30) + new Random().nextInt(60)), syncFn)
                log.error("Register up error. " + (ex.message?:ex.class.simpleName))
            } finally {
                running.set(false)
            }
        }
    }


    /**
     * 自己的信息
     * 例: {"id":"rc_b70d18d52269451291ea6380325e2a84", "name":"rc", "tcp":"192.168.56.1:8001", "http":"localhost:8000"}
     */
    def getSelfInfo() {
        JSONObject data = new JSONObject(4)
        if (app.id && app.name) {
            data.put("id", app.id)
            data.put("name", app.name)
        } else {
            log.error("Current App name or id is empty"); return null
        }

        def exposeHttp = getStr('exposeHttp', null) // 配置暴露的http
        if (exposeHttp) data.put("http", exposeHttp)
        else Optional.ofNullable(ep.fire("http.hp")).ifPresent{ data.put("http", it) }

        def exposeTcp = getStr('exposeTcp', null) // 配置暴露的tcp
        if (exposeTcp) data.put("tcp", exposeTcp)
        else if (tcpServer.hp.split(":")[0]) data.put("tcp", tcpServer.hp)
        else {
            def ip = ipv4()
            if (ip) {
                data.put("tcp", ip + tcpServer.hp)
            } else {
                log.warn("Can't get local ipv4")
                return null
            }
        }

        return data
    }
}

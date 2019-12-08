package core.module.remote

import cn.xnatural.enet.event.EC
import cn.xnatural.enet.event.EL
import cn.xnatural.enet.event.EP
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import core.AppContext
import core.Value
import core.module.ServerTpl
import org.h2.server.TcpServer

import javax.annotation.Resource
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.Consumer

class Remoter extends ServerTpl {
    @Resource
    protected AppContext app
    @Resource
    protected       Executor exec
    /**
     * ecId -> EC
     */
    protected   final     Map<String, EC> ecMap   = new ConcurrentHashMap<>()
    /**
     * 系统名字(标识)
     */
    @Value('$sys.name')
    protected       String          sysName
    protected       TCPClient       tcpClient
    protected       TCPServer       tcpServer
    // 集群的服务中心地址 host:port,host1:port2
    @Lazy
    protected final String master = getStr('master', null)


    Remoter() { super("remote") }


    @EL(name = "sys.starting")
    def start() {
        if (tcpClient || tcpServer) throw new RuntimeException("$name is already running")
        if (exec == null) exec = Executors.newFixedThreadPool(2)
        if (ep == null) {ep = new EP(exec); ep.addListenerSource(this)}

        if (!ep.exist("sched.after")) throw new RuntimeException("Need sched Server!")

        tcpClient = bean(TCPClient)
        if (tcpClient == null) {
            tcpClient = new TCPClient()
            exposeBean(tcpClient)
            tcpClient.start()
        }

        tcpServer = bean(TcpServer)
        if (tcpServer == null) {
            tcpServer = new TCPServer()
            exposeBean(tcpServer)
            tcpServer.start()
        }

        ep.fire("${name}.started")
    }


    @EL(name = "sys.stopping", async = false)
    def stop() {
        findLocalBean(null, TCPClient, null)?.stop()
        findLocalBean(null, TCPServer, null)?.stop()
        if (exec instanceof ExecutorService) ((ExecutorService) exec).shutdown()
    }


    @EL(name = 'sys.started')
    def init() {
        tcpClient.handlers.add{jo ->
            if ("event"== jo['type']) {
                exec.execute(() -> receiveEventResp(jo.getJSONObject("data")))
            }
        }
    }


    /**
     * 调用远程事件
     * 执行流程: 1. 客户端发送事件:  {@link #sendEvent(EC, String, String, Object[])}
     *          2. 服务端接收到事件: {@link #receiveEventReq(com.alibaba.fastjson.JSONObject, java.util.function.Consumer)}
     *          3. 客户端接收到返回: {@link #receiveEventResp(com.alibaba.fastjson.JSONObject)}
     * @param ec
     * @param appName 应用名字
     * @param eName 要触发的事件名
     * @param remoteMethodArgs 远程事件监听方法的参数
     */
    @EL(name = "remote")
    protected void sendEvent(EC ec, String appName, String eName, Object[] remoteMethodArgs) {
        if (tcpClient == null) throw new RuntimeException("$name not is running")
        if (appName == null) throw new IllegalArgumentException("appName is empty")
        ec.suspend()
        JSONObject params = new JSONObject(4)
        try {
            if (ec.id() == null) {ec.id(UUID.randomUUID().toString())}
            params.put("eId", ec.id())
            // 是否需要远程响应执行结果(有完成回调函数就需要远程响应调用结果)
            boolean reply = ec.completeFn() != null
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
            JSONObject data = new JSONObject(3).fluentPut("type", "event").fluentPut("source", sysName).fluentPut("data", params)
            tcpClient.send(appName, data)
            if (reply) { // 数据发送成功. 如果需要响应, 则添加等待响应超时处理
                ep.fire("sched.after", Duration.ofSeconds(getInteger("eventTimeout", 20)), {
                    ecMap.remove(ec.id())?.errMsg("'" + eName + "' Timeout").resume().tryFinish()
                })
            }
        } catch (Throwable ex) {
            log.error(ex, "Error fire remote event to '{}'. params: {}", appName, params)
            ecMap.remove(ec.id())
            ec.ex(ex).resume()
        }
    }


    /**
     * 接收远程事件返回的数据. 和 {@link #sendEvent(EC, String, String, Object[])} 对应
     * @param data
     */
    protected void receiveEventResp(JSONObject data) {
        log.debug("Receive event response: {}", data)
        EC ec = ecMap.remove(data.getString("eId"))
        if (ec != null) ec.errMsg(data.getString("exMsg")).result(data.get("result")).resume().tryFinish()
    }


    /**
     * 接收远程事件的执行请求
     * @param data 数据
     * @param reply 响应回调(传参为响应的数据)
     */
    protected void receiveEventReq(JSONObject data, Consumer<String> reply) {
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
                    reply.accept(new JSONObject(3).fluentPut("type", "event").fluentPut("source", sysName).fluentPut("data", r))
                })
            }
            ep.fire(eName, ec)
        } catch (Throwable ex) {
            if (fReply) {
                JSONObject r = new JSONObject(4)
                r.put("eId", data.getString("eId"))
                r.put("success", false)
                r.put("result", null)
                r.put("exMsg", ex.getMessage() ? ex.getMessage(): ex.getClass().name)
                reply.accept(new JSONObject(3).fluentPut("type", "event").fluentPut("source", sysName).fluentPut("data", r))
            }
            log.error("invoke event error. data: " + data, ex)
        }
    }


    /**
     * 自己的信息
     * 例: {"id":"rc_b70d18d5-2269-4512-91ea-6380325e2a84", "name":"rc", "tcp":"192.168.56.1:8001", "http":"localhost:8000"}
     */
    def getSelfInfo() {
        JSONObject data = new JSONObject(4)
        data.put("id", app.id)
        data.put("name", app.name)
        Optional.ofNullable(ep.fire("http.getHp")).ifPresent{ data.put("http", it) }
        data.put("tcp", tcpServer.hp)

        if (!data.containsKey('tcp') && !data.containsKey('http')) return null
        return data
    }


    /**
     * 解析出本地ip
     * @return
     */
    protected String resolveLocalIp() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface current = en.nextElement()
                if (!current.isUp() || current.isLoopback() || current.isVirtual()) continue
                Enumeration<InetAddress> addresses = current.getInetAddresses()
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement()
                    if (addr.isLoopbackAddress()) continue
                    return addr.getHostAddress()
                }
            }
        } catch (SocketException e) { log.error('', e) }
        return ""
    }
}

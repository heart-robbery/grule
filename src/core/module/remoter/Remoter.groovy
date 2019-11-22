package core.module.remoter

import cn.xnatural.enet.event.EC
import cn.xnatural.enet.event.EL
import cn.xnatural.enet.event.EP
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import core.Value
import core.module.ServerTpl
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled

import javax.annotation.Resource
import java.nio.charset.Charset
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.Consumer

class Remoter extends ServerTpl {
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
    /**
     * 数据传输分割符.用于tcp粘包/拆包
     */
    protected       ByteBuf delimiter


    Remoter() { super("remote") }


    @EL(name = "sys.starting")
    def start() {
        if (tcpClient || tcpServer) throw new RuntimeException("$name is already running")
        if (exec == null) exec = Executors.newFixedThreadPool(2)
        if (ep == null) {ep = new EP(exec); ep.addListenerSource(this)}

        if (!ep.exist("sched.after")) throw new RuntimeException("Need sched Server!");

        // sysName = (String) ep.fire("sysName");
        if (!sysName) throw new IllegalArgumentException("'sys.name' not config");
        delimiter = Unpooled.copiedBuffer(getStr("delimiter", "$_$").getBytes(Charset.forName("utf-8")));

        tcpClient = new TCPClient()
        tcpClient.start()

        tcpServer = new TCPServer(this, ep, exec);
        tcpServer.start();

        ep.fire(getName() + ".started");
    }


    @EL(name = "sys.stopping", async = false)
    public void stop() {
        if (tcpClient != null) tcpClient.stop();
        if (tcpServer != null) tcpServer.stop();
        if (exec instanceof ExecutorService) ((ExecutorService) exec).shutdown();
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
        if (tcpClient == null) throw new RuntimeException(getName() + " not is running");
        if (appName == null) throw new IllegalArgumentException("appName is empty");
        ec.suspend();
        JSONObject params = new JSONObject(4);
        try {
            if (isEmpty(ec.id())) ec.id(UUID.randomUUID().toString());
            params.put("eId", ec.id());
            // 是否需要远程响应执行结果(有完成回调函数就需要远程响应调用结果)
            boolean reply = ec.completeFn() != null;
            params.put("reply", reply);
            params.put("eName", eName);
            if (remoteMethodArgs != null) {
                JSONArray args = new JSONArray(remoteMethodArgs.length);
                params.put("args", args);
                for (Object arg : remoteMethodArgs) {
                    if (arg == null) args.add(new JSONObject(0));
                    else args.add(new JSONObject(2).fluentPut("type", arg.getClass().getName()).fluentPut("value", arg));
                }
            }
            if (reply) ecMap.put(ec.id(), ec);

            // 发送请求给远程应用appName执行. 消息类型为: 'event'
            JSONObject data = new JSONObject(3).fluentPut("type", "event").fluentPut("source", sysName).fluentPut("data", params);
            tcpClient.send(appName, data, ex -> {
                if (ex == null) {
                    if (reply) { // 数据发送成功. 如果需要响应, 则添加等待响应超时处理
                        ep.fire("sched.after", Duration.ofSeconds(getInteger("eventTimeout", 17)), (Runnable) () -> {
                            EC e = ecMap.remove(ec.id());
                            if (e != null) { e.errMsg("'" + eName + "' Timeout").resume().tryFinish(); }
                        });
                    }
                    log.debug("Fire remote event to '{}'. params: {}", appName, params);
                } else if (ex != null) { // 数据发送失败
                    ecMap.remove(ec.id());
                    log.error(ex, "Error fire remote event to '{}'. params: {}", appName, params);
                    ec.ex(ex).resume().tryFinish();
                }
            });
        } catch (Throwable ex) {
            log.error(ex, "Error fire remote event to '{}'. params: {}", appName, params);
            ecMap.remove(ec.id()); ec.ex(ex).resume();
        }
    }


    /**
     * 接收远程事件返回的数据. 和 {@link #sendEvent(EC, String, String, Object[])} 对应
     * @param data
     */
    protected void receiveEventResp(JSONObject data) {
        log.debug("Receive event response: {}", data);
        EC ec = ecMap.remove(data.getString("eId"));
        if (ec != null) ec.errMsg(data.getString("exMsg")).result(data.get("result")).resume().tryFinish();
    }


    /**
     * 接收远程事件的执行请求
     * @param data 数据
     * @param reply 响应回调(传参为响应的数据)
     */
    protected void receiveEventReq(JSONObject data, Consumer<Object> reply) {
        log.debug("Receive event request: {}", data);
        boolean fReply = Boolean.TRUE.equals(data.getBoolean("reply")); // 是否需要响应
        try {
            String eId = data.getString("eId");
            String eName = data.getString("eName");

            EC ec = new EC();
            ec.id(eId);
            ec.args(data.getJSONArray("args") == null ? null : data.getJSONArray("args").stream().map(o -> {
                JSONObject jo = (JSONObject) o;
                String t = jo.getString("type");
                if (jo.isEmpty()) return null; // 参数为null
                else if (String.class.getName().equals(t)) return jo.getString("value");
                else if (Boolean.class.getName().equals(t)) return jo.getBoolean("value");
                else if (Integer.class.getName().equals(t)) return jo.getInteger("value");
                else if (Short.class.getName().equals(t)) return jo.getShort("value");
                else if (Long.class.getName().equals(t)) return jo.getLong("value");
                else if (Double.class.getName().equals(t)) return jo.getDouble("value");
                else if (Float.class.getName().equals(t)) return jo.getFloat("value");
                else if (BigDecimal.class.getName().equals(t)) return jo.getBigDecimal("value");
                else if (JSONObject.class.getName().equals(t) || Map.class.equals(t)) return jo.getJSONObject("value");
                else if (JSONArray.class.getName().equals(t) || List.class.equals(t)) return jo.getJSONArray("value");
                else throw new IllegalArgumentException("Not support parameter type '" + t + "'");
            }).toArray());

            if (fReply) {
                ec.completeFn(ec1 -> {
                    JSONObject r = new JSONObject(3);
                    r.put("eId", ec.id());
                    if (!ec.isSuccess()) { r.put("exMsg", ec.failDesc()); }
                    r.put("result", ec.result);
                    reply.accept(new JSONObject(3).fluentPut("type", "event").fluentPut("source", sysName).fluentPut("data", r));
                });
            }
            ep.fire(eName, ec);
        } catch (Exception ex) {
            if (fReply) {
                JSONObject r = new JSONObject(4);
                r.put("eId", data.getString("eId"));
                r.put("success", false);
                r.put("result", null);
                r.put("exMsg", isEmpty(ex.getMessage()) ? ex.getClass().getName() : ex.getMessage());
                reply.accept(new JSONObject(3).fluentPut("type", "event").fluentPut("source", sysName).fluentPut("data", r));
            }
            log.error(ex, "invoke event error. data: " + data);
        }
    }


    /**
     * 解析出本地ip
     * @return
     */
    protected String resolveLocalIp() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface current = en.nextElement();
                if (!current.isUp() || current.isLoopback() || current.isVirtual()) continue;
                Enumeration<InetAddress> addresses = current.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr.isLoopbackAddress()) continue;
                    return addr.getHostAddress();
                }
            }
        } catch (SocketException e) { log.error(e); }
        return "";
    }


    /**
     * 判断系统是否为 linux 系统
     * 判断方法来源 {@link io.netty.channel.epoll.Native#loadNativeLibrary()}
     * @return
     */
    protected boolean isLinux() {
        return System.getProperty("os.name").toLowerCase(Locale.UK).trim().startsWith("linux");
    }


    /**
     * 消息转换成 ByteBuf
     * @param msg
     * @return {@link ByteBuf}
     */
    protected ByteBuf toByteBuf(Object msg) {
        String end = delimiter.toString(Charset.forName("utf-8")); // 每条消息的结束符.解决tcp粘包/拆包
        if (msg instanceof String) return Unpooled.copiedBuffer(msg + end, Charset.forName("utf-8"));
        else if (msg instanceof JSONObject) return Unpooled.copiedBuffer(((JSONObject) msg).toJSONString() + end, Charset.forName("utf-8"));
        else throw new IllegalArgumentException("Not support send data type '" + (msg == null ? null : msg.getClass().getName()) + "'");
    }
}

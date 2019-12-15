package core.module.remote

import cn.xnatural.enet.event.EC
import cn.xnatural.enet.event.EL
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONException
import com.alibaba.fastjson.JSONObject
import core.AppContext
import core.Devourer
import core.module.ServerTpl
import groovy.transform.PackageScope
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.DelimiterBasedFrameDecoder
import io.netty.handler.timeout.IdleStateEvent
import io.netty.handler.timeout.IdleStateHandler
import io.netty.util.ReferenceCountUtil

import javax.annotation.Resource
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.LongAdder
import java.util.function.Consumer

import static core.Utils.linux
import static java.util.concurrent.TimeUnit.SECONDS

class TCPServer extends ServerTpl {
    @Lazy
    protected       Remoter                                remoter    = bean(Remoter)
    @Resource
    protected       AppContext                             app
    @Resource
    protected       Executor                               exec
    @Lazy
    protected       String                                 hp         = getStr('hp', '')
    @Lazy
    protected       String                                 delimiter  = getStr('delimiter', '')
    protected       EventLoopGroup                         boos
    /**
     * 当前连接数
     */
    protected final AtomicInteger                          connCount  = new AtomicInteger(0)
    /**
     * 保存 app info 的属性信息
     */
    protected final Map<String, List<Map<String, Object>>> appInfoMap = new ConcurrentHashMap<>()
    final           Devourer                               upDevourer = new Devourer("registerUp", exec)
    final           List<Consumer<JSONObject>>             handlers   = new LinkedList<>()


    TCPServer() { super("tcp-server") }


    @EL(name = 'sys.starting')
    def start() {
        if (boos) throw new RuntimeException("$name is already running")
        if (!hp) throw new RuntimeException("'${name}.hp' not config. format is '[host]:port'")
        create()
        ep.fire("${name}.started")
    }


    @EL(name = 'sys.stopping')
    def stop() {
        log.info("Close '{}'", name)
        boos?.shutdownGracefully()
        upDevourer?.shutdown()
    }



    /**
     * 创建tcp(netty)服务端
     */
    protected def create() {
        String loopType = getStr("loopType", (isLinux() ? "epoll" : "nio"))
        Class chClz
        if ("epoll".equalsIgnoreCase(loopType)) {
            boos = new EpollEventLoopGroup(getInteger("threads-boos", 1), exec)
            chClz = EpollServerSocketChannel.class
        } else if ("nio".equalsIgnoreCase(loopType)) {
            boos = new NioEventLoopGroup(getInteger("threads-boos", 1), exec)
            chClz = NioServerSocketChannel.class
        }
        ServerBootstrap sb = new ServerBootstrap()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, getInteger('connectTimeout', 5000))
                .option(ChannelOption.SO_TIMEOUT, getInteger("soTimeout", 10000))
                .group(boos).channel(chClz)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            void channelRegistered(ChannelHandlerContext ctx) throws Exception {
                                if (!fusing(ctx)) {
                                    connCount.incrementAndGet()
                                    log.debug("TCP Connection registered: {}", connCount)
                                    super.channelRegistered(ctx)
                                }
                            }
                            @Override
                            void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
                                super.channelUnregistered(ctx); connCount.decrementAndGet()
                                log.debug("TCP Connection unregistered: {}, addr: '{}'-'{}'", connCount, ch.remoteAddress(), ch.localAddress())
                            }
                        })
                        // 最好是将IdleStateHandler放在入站的开头，并且重写userEventTriggered这个方法的handler必须在其后面。否则无法触发这个事件。
                        ch.pipeline().addLast(new IdleStateHandler(getLong("readerIdleTime", 10 * 60L), getLong("writerIdleTime", 0L), getLong("allIdleTime", 0L), SECONDS))
                        ch.pipeline().addLast(new DelimiterBasedFrameDecoder(
                                getInteger("maxFrameLength", 1024 * 1024),
                                Unpooled.copiedBuffer((delimiter?:'').getBytes("utf-8"))
                        ))
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                log.error("server side error", cause)
                            }
                            @Override
                            void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                if (evt instanceof IdleStateEvent) { ctx.close() }
                                else super.userEventTriggered(ctx, evt)
                            }
                            @Override
                            void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                ByteBuf buf = (ByteBuf) msg
                                String str
                                try {
                                    str = buf.toString(Charset.forName("utf-8"))
                                    handleReceive(ctx, str)
                                } catch (JSONException ex) {
                                    log.error("Received Error Data from '{}'. data: {}, errMsg: " + ex.message, ctx.channel().remoteAddress(), str)
                                    ctx.close()
                                } catch (Throwable ex) {
                                    ctx.close(); log.error('handleReceive error', ex)
                                } finally {
                                    ReferenceCountUtil.release(msg)
                                }
                            }
                        })
                    }
                })
                .childOption(ChannelOption.SO_KEEPALIVE, true)

        def arr = hp.trim().split(":")
        if (arr[0]) sb.bind(arr[0], arr[1]).sync()
        else sb.bind(Integer.valueOf(arr[1]))
        log.info("Start listen TCP {}, type: {}", hp, loopType)
    }


    /**
     * 处理接收来自己客户端的数据
     * @param ctx
     * @param dataStr 客户端发送过来的字符串
     */
    protected def handleReceive(ChannelHandlerContext ctx, String dataStr) {
        log.trace("Receive client '{}' data: {}", ctx.channel().remoteAddress(), dataStr)
        count() // 统计

        JSONObject jo = JSON.parseObject(dataStr)
        handlers.each {it.accept(jo)}

        String t = jo.getString("type")
        if ("event" == t) { // 远程事件请求
            exec.execute{
                remoter?.receiveEventReq(jo.getJSONObject("data"), {o -> ctx.writeAndFlush(Unpooled.copiedBuffer((o + (delimiter?:'')).getBytes('utf-8')))})
            }
        } else if ("appUp" == t) { // 应用注册在线通知
            upDevourer.offer{
                JSONObject d
                try { d = jo.getJSONObject("data"); appUp(d, ctx) }
                catch (Exception ex) {
                    log.error("Register up error!. data: " + d, ex)
                }
            }
        } else if ("cmd-log" == t) { // telnet 命令行设置日志等级
            // telnet localhost 8001
            // 例: {"type":"cmd-log", "source": "xxx", "data": "core.module.remote: debug"}$_$
            exec.execute{
                String[] arr = jo.getString("data").split(":")
                // Log.setLevel(arr[0].trim(), arr[1].trim())
                ctx.writeAndFlush(Unpooled.copiedBuffer("set log level success", Charset.forName("utf-8")))
            }
        } else if ("cmd-restart-server" == t) { // telnet 命令行重启某个服务
            // telnet localhost 8001
            // 例: {"type":"cmd-restart-server", "source": "xxx", "data": "ehcache"}$_$
            String sName = jo.getString("data")
            ep.fire(sName+ ".stop", EC.of(this).completeFn{ec -> ep.fire(sName + ".start")})
        } else {
            ctx.close()
            log.error("Not support exchange data type '{}'", t)
        }
    }


    /**
     * 熔断: 是否拒绝处理请求
     * @param ctx
     * @return
     */
    protected boolean fusing(ChannelHandlerContext ctx) {
        if (connCount.get() >= getInteger("maxConnection", 100)) { // 最大连接
            ctx.writeAndFlush(Unpooled.copiedBuffer("server is busy", Charset.forName("utf-8")))
            ctx.close()
            return true
        }
        return false
    }


    /**
     * 应用上线通知
     * NOTE: 此方法线程已安全
     * 例: {"name": "应用名", "id": "应用实例id", "tcp":"localhost:8001", "http":"localhost:8080", "udp": "localhost:11111"}
     * @param data
     * @param ctx
     */
    @PackageScope
    def appUp(final JSONObject data, final ChannelHandlerContext ctx) {
        if (!data) { log.warn("Register data is empty"); return}
        log.debug("Receive register up: {}", data)
        if (!data['name'] || !data['id']) { // 数据验证
            throw new IllegalArgumentException("app register up info bad data: " + data)
        }
        data["_time"] = System.currentTimeMillis()

        //1. 先删除之前的数据,再添加新的
        boolean isNew = true
        List<Map<String, Object>> apps = appInfoMap.computeIfAbsent(data["name"], {new LinkedList<>()})
        for (Iterator<Map<String, Object>> it = apps.iterator(); it.hasNext(); ) {
            if (it.next()["id"] == data["id"]) {it.remove(); isNew = false; break}
        }
        apps << data
        if (isNew && data['id'] != app.id) log.info("New app '{}' online. {}", data["name"], data)

        //2. 遍历所有的数据,删除不必要的数据
        for (Iterator<Map.Entry<String, List<Map<String, Object>>>> it = appInfoMap.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, List<Map<String, Object>>> e = it.next()
            if (!e.value) {it.remove(); continue} // 删除没有对应的服务信息的应用
            for (Iterator<Map<String, Object>> it2 = e.value.iterator(); it2.hasNext(); ) {
                Map<String, Object> cur = it2.next()
                if (!cur) {it2.remove(); continue} // 删除空的坏数据
                // 删除一段时间未活动的注册信息, dropAppTimeout 单位: 分钟
                if ((System.currentTimeMillis() - (Long) cur.getOrDefault("_time", System.currentTimeMillis()) > getInteger("dropAppTimeout", 30) * 60 * 1000) && (cur["id"] != app.id)) {
                    it2.remove()
                    log.warn("Drop timeout app register info: {}", cur)
                }
            }
        }

        //3. 同步注册信息
        if (data['id'] != app.id) ep.fire("updateAppInfo", data) // 同步信息给本服务器的tcp-client
        appInfoMap.each {e ->
            e.value.each {d ->
                // 返回所有的注册信息给当前来注册的客户端
                if (d["id"] != data["id"]) {
                    ctx?.writeAndFlush(Unpooled.copiedBuffer(new JSONObject(2).fluentPut("type", "updateAppInfo").fluentPut("data", d).toString() + (delimiter?:'')))
                }
                // 如果是新系统上线, 则主动通知其它系统
                if (isNew && d['id'] != data['id'] && d['id'] != app.id) {
                    ep.fire("remote", EC.of(this).attr('toAll', true).args(e.key, "updateAppInfo", new Object[]{data}))
                }
            }
        }
    }



    /**
     * 统计每小时的处理 tcp 数据包个数
     * MM-dd HH -> 个数
     */
    protected final Map<String, LongAdder> hourCount = new ConcurrentHashMap<>(3)
    protected void count() {
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH")
        boolean isNew = false
        String hStr = sdf.format(new Date())
        LongAdder count = hourCount.get(hStr)
        if (count == null) {
            synchronized (hourCount) {
                count = hourCount.get(hStr)
                if (count == null) {
                    count = new LongAdder(); hourCount.put(hStr, count)
                    isNew = true
                }
            }
        }
        count.increment()
        if (isNew) {
            final Calendar cal = Calendar.getInstance()
            cal.setTime(new Date())
            cal.add(Calendar.HOUR_OF_DAY, -1)
            String lastHour = sdf.format(cal.getTime())
            LongAdder c = hourCount.remove(lastHour)
            if (c != null) log.info("{} 时共处理 tcp 数据包: {} 个", lastHour, c)
        }
    }
}

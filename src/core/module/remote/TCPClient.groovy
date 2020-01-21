package core.module.remote

import cn.xnatural.enet.event.EL
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONException
import com.alibaba.fastjson.JSONObject
import core.AppContext
import core.module.SchedSrv
import core.module.ServerTpl
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollSocketChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.DelimiterBasedFrameDecoder
import io.netty.util.AttributeKey
import io.netty.util.ReferenceCountUtil
import sun.net.util.IPAddressUtil

import javax.annotation.Resource
import java.nio.channels.ClosedChannelException
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Consumer

import static core.Utils.linux

class TCPClient extends ServerTpl {
    @Lazy AppContext app = bean(AppContext)
    @Lazy Remoter remoter = bean(Remoter)
    @Lazy SchedSrv sched = bean(SchedSrv)
    @Lazy Executor exec = bean(Executor)
    final     Map<String, Node>          hpNodes   = new ConcurrentHashMap<>()
    final     Map<String, AppGroup>      apps      = new ConcurrentHashMap<>()
    protected EventLoopGroup             boos
    protected Bootstrap                  boot
    @Lazy String                         delimiter = getStr('delimiter', '')
    final     List<Consumer<JSONObject>> handlers  = new LinkedList<>()


    TCPClient() { super("tcpClient") }
    TCPClient(String name) { super(name) }


    @EL(name = 'sys.starting')
    def start() {
        create()
        ep.fire("${name}.started")
    }


    @EL(name = 'sys.stopping')
    def stop() {
        boos?.shutdownGracefully()
        boot = null
    }


    @EL(name = 'sys.started', async = false)
    def started() {
        def node = getStr('node', null)
        if (node) JSON.parseArray(node).each {JSONObject it -> updateAppInfo(it) }
    }


    /**
     * 更新 app 信息
     * @param data app node信息
     *  例: {"name":"rc", "id":"rc_b70d18d5-2269-4512-91ea-6380325e2a84", "tcp":"192.168.56.1:8001","http":"localhost:8000"}
     */
    @EL(name = "updateAppInfo")
    def updateAppInfo(final JSONObject data) {
        log.trace("Update app info: {}", data)
        if (data == null || data.isEmpty()) return
        if (app.id == data["id"] || remoter.selfInfo?['tcp'] == data['tcp']) return // 不把系统本身的信息放进去

        apps.computeIfAbsent(data["name"], {s -> {
            log.info("New app group '{}'", s)
            new AppGroup(name: s)
        }}).updateNode(data)
    }


    /**
     * 发送数据到host:port
     * @param host 主机地址
     * @param port 端口
     * @param data 要发送的数据
     * @param failFn 失败回调函数(可不传)
     */
    def send(String host, Integer port, String data, Consumer<Throwable> failFn = null) {
        log.debug("Send data to '{}:{}'. data: " + data, host, port)
        hpNodes.computeIfAbsent("$host:$port", {s ->
            def n = new Node(tcpHp: s)
            log.info("New Node added. tcpHp: {}", n.tcpHp)
            n
        }).send(data, failFn)
    }


    /**
     * 向app发送数据
     * @param appName 应用名
     * @param data 要发送的数据
     * @param target 可用值: 'any', 'all'
     */
    def send(String appName, String data, String target = 'any') {
        def group = apps.get(appName)
        if (group == null) throw new RuntimeException("Not found app '$appName' system online")
        if ('any' == target) {
            group.sendToAny(data)
        } else if ('all' == target) {
            group.nodes.values().each {
                try {
                    it.send(data)
                } catch (Throwable th) {
                    log.error("Send data: '{}' fail. Node: '{}'. errMsg: {}" + (th.message?:th.class.simpleName), data, it)
                }
            }
        } else {
            throw new IllegalArgumentException("Not support target '$target'")
        }
    }


    /**
     * 创建 tcp(netty) 客户端
     */
    protected create() {
        String loopType = getStr("loopType", (isLinux() ? "epoll" : "nio"))
        Class chClz
        if ("epoll".equalsIgnoreCase(loopType)) {
            boos = new EpollEventLoopGroup(getInteger("threads-boos", 1), exec)
            chClz = EpollSocketChannel.class
        } else if ("nio".equalsIgnoreCase(loopType)) {
            boos = new NioEventLoopGroup(getInteger("threads-boos", 1), exec)
            chClz = NioSocketChannel.class
        }
        boot = new Bootstrap().group(boos).channel(chClz)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, getInteger('connectTimeout', 5000))
                // .option(ChannelOption.SO_TIMEOUT, getInteger("soTimeout", 10000))
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        // ch.pipeline().addLast(new IdleStateHandler(getLong("readerIdleTime", 12 * 60 * 60L), getLong("writerIdleTime", 10L), getLong("allIdleTime", 0L), SECONDS))
                        if (delimiter) {
                            ch.pipeline().addLast(
                                    new DelimiterBasedFrameDecoder(
                                            getInteger("maxFrameLength", 1024 * 1024),
                                            Unpooled.copiedBuffer(delimiter.getBytes("utf-8"))
                                    )
                            )
                        }
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
                                ((Runnable) ctx.channel().attr(AttributeKey.valueOf("removeFn")).get())?.run()
                                super.channelUnregistered(ctx)
                            }

                            @Override
                            void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                log.error(name + " error", cause)
                            }
                            @Override
                            void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                ByteBuf buf = (ByteBuf) msg
                                String str = null
                                try {
                                    str = buf.toString(Charset.forName("utf-8"))
                                    receiveReply(ctx, str)
                                } catch (JSONException ex) {
                                    log.error("Received Error Data from '{}'. data: {}, errMsg: {}", ctx.channel().remoteAddress(), str, ex.getMessage())
                                    ctx.close()
                                } finally {
                                    ReferenceCountUtil.release(msg)
                                }
                            }
                        })
                    }
                })
    }


    /**
     * 客户端接收到服务端的响应数据
     * @param ctx
     * @param dataStr
     */
    protected receiveReply(ChannelHandlerContext ctx, String dataStr) {
        log.trace("Receive reply from '{}': {}", ctx.channel().remoteAddress(), dataStr)
        def jo = JSON.parseObject(dataStr)
        if ("updateAppInfo" == jo['type']) {
            exec.execute{
                JSONObject d
                try { d = jo.getJSONObject("data"); updateAppInfo(d) }
                catch (Throwable ex) {
                    log.error("updateAppInfo error. data: " + d, ex)
                }
            }
        }
        handlers.each {it.accept(jo)}
    }


    // 字符串 转换成 ByteBuf
    ByteBuf toByteBuf(String data) { Unpooled.copiedBuffer((data + (delimiter?:'')).getBytes('utf-8')) }


    /**
     * 提供dns 解析服务
     * 根据appName 转换 成 ip
     * @param hostname
     * @return
     */
    @EL(name = "dns", async = false)
    InetAddress dns(String hostname) {
        def ip = ((Node) apps.get(hostname).nodes.find()?.value)?.tcpHp?.split(":")?[0]
        if(ip) {
            if ("localhost" == ip) ip = "127.0.0.1"
            return InetAddress.getByAddress(hostname, IPAddressUtil.textToNumericFormatV4(ip))
        }
        null
    }


    /**
     * 提供http 解析服务
     * 根据appName 转换 成 http的 ip:port
     * @param appName
     * @return
     */
    @EL(name = "resolveHttp", async = false)
    String resolveHttp(String appName) {
        return ((Node) apps.get(appName).nodes.find()?.value)?.httpHp
    }


    // 应用组
    class AppGroup {
        String name
        // 节点 tcpHp -> 节点
        final Map<String, Node> nodes =  new ConcurrentHashMap<>()

        // 更新/添加应用节点
        def updateNode(final JSONObject data) {
            def (tcpHp, httpHp) = [data['tcp'], data['http']]
            if (!tcpHp) { // tcpHp 不能为空
                throw new IllegalArgumentException("Node illegal error. $tcpHp, $httpHp")
            }
            def n = nodes.get(tcpHp)
            if (n) { // 更新
                n.httpHp = httpHp
            } else {
                n = new Node(group: this, httpHp: httpHp, tcpHp: tcpHp)
                nodes.put(tcpHp, n)
                log.info("New Node added. Node: '{}'", n)
            }
        }

        // 随机选择一个节点发送数据
        def sendToAny(final String data) {
            List<Node> ns = new LinkedList<>()
            nodes.each {ns.add(it.value)}
            if (ns.size() == 0) {
                throw new RuntimeException("Not found available node for '$name'")
            }
            randomStrategy(ns, data)
        }

        // 随机节点策略
        def randomStrategy(List<Node> ns, String data) {
            if (ns.size() == 1) ns[0].send(data)
            else {
                def n = ns[new Random().nextInt(ns.size())]
                n.send(data, {th -> // 尽可能找到可用的节点把数据发送出去
                    ns.remove(n)
                    randomStrategy(ns, data)
                })
            }
        }

        def setName(String name) {
            if (this.name != null) throw new RuntimeException("'name' not allow change")
            this.name = name
        }
    }


    // 实例节点
    protected class Node {
        AppGroup          group
        String            tcpHp
        String            httpHp
        final ChannelPool tcpPool = new ChannelPool()

        def setTcpHp(String hp) {
            if (!hp) throw new IllegalArgumentException("Node tcpHp must not be empty")
            this.tcpHp = hp
            def arr = hp.split(":")
            tcpPool.host = arr[0].trim()
            tcpPool.port = Integer.valueOf(arr[1].trim())
            tcpPool.node = this
            // exec.execute{try {tcpPool.create()} catch (Exception ex) {log.error('', ex)}} // 默认创建一个
        }

        // 发送数据
        def send(final String data, Consumer<Throwable> failFn = null, AtomicInteger limit = new AtomicInteger(0)) {
            try {
                def ch = tcpPool.get()
                ch.writeAndFlush(toByteBuf(data)).addListener{ChannelFuture cf ->
                    if (cf.cause() != null) {
                        if (cf.cause() instanceof ClosedChannelException) {
                            tcpPool.remove(ch)
                            if (limit.get() < 3) {
                                limit.incrementAndGet()
                                send(data, failFn, limit) // 重试
                                return
                            }
                        }
                        if (failFn != null) failFn.accept(cf.cause())
                        else {
                            log.error("Send data: '{}' fail. Node: '{}'. errMsg: {}" + (cf.cause().message?:cf.cause().class.simpleName), data, this)
                        }
                    }
                }
            } catch (Throwable th) {
                if (failFn) failFn.accept(th)
                else throw th
            }
        }

        @Override
        String toString() {
            '[' + (group ? "name:$group.name, " : '') + ("tcpHp:$tcpHp, ") + (httpHp ? "httpHp:$httpHp, " : '') + ("poolSize:${tcpPool.chs.size()}") + ']'
        }
    }


    // netty Channel 连接池
    protected class ChannelPool {
              Node          node
              String        host
              Integer       port
        final ReadWriteLock rwLock = new ReentrantReadWriteLock()
        final List<Channel> chs    = new ArrayList<>(7)

        // 获取连接Channel
        def get() {
            if (chs.size() == 0) create()
            if (chs.size() == 0) {
                throw new RuntimeException("Not found available Channel for '${node.toString()}'")
            }
            try {
                rwLock.readLock().lock()
                if (chs.size() == 1) {
                    return chs.get(0)
                } else {
                    return chs.get(new Random().nextInt(chs.size())) // 随机选择连接
                }
            } finally {
                rwLock.readLock().unlock()
            }
            null
        }

        // 创建新连接Channel
        def create() {
            Channel ch
            try { // 连接
                ch = boot.connect(host, port).sync().channel()
                ch.attr(AttributeKey.valueOf("removeFn")).set({ remove(ch) } as Runnable)
            } catch (Throwable th) {
                // log.error("Create TCP Connection error. node: '{}'. errMsg: {}", node, (th.message ?: th.class.simpleName))
                if (node.group && node.group.nodes.size() > 1) { // 删除连接不上的节点, 但保留一个
                    node.group?.nodes.remove(node.tcpHp)
                }
                throw new RuntimeException("Create TCP Connection error. node: '${node}'. errMsg: ${(th.message ?: th.class.simpleName)}", th)
            }
            if (ch != null) {
                try { // 添加连接
                    rwLock.writeLock().lock()
                    chs.add(ch)
                    log.info("New TCP Connection to '{}'", node)
                } finally {
                    rwLock.writeLock().unlock()
                }
            }
            ch
        }

        // 删除连接Channel
        def remove(final Channel ch) {
            try {
                rwLock.writeLock().lock()
                for (def it = chs.iterator(); it.hasNext();) {
                    if (it.next() == ch) {
                        it.remove()
                        log.info("Remove TCP connection for Node '{}'", node)
                        break
                    }
                }
            } finally {
                rwLock.writeLock().unlock()
            }
        }
    }
}
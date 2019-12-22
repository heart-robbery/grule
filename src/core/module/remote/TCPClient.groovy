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

import javax.annotation.Resource
import java.nio.channels.ClosedChannelException
import java.nio.charset.Charset
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Consumer

import static core.Utils.linux

class TCPClient extends ServerTpl {
    @Resource
    protected AppContext                 app
    @Resource
    protected SchedSrv  sched
    @Resource
    protected Executor                   exec
    final     Map<String, Node>          hpNodes   = new ConcurrentHashMap<>()
    final     Map<String, AppGroup>      apps      = new ConcurrentHashMap<>()
    protected EventLoopGroup             boos
    protected Bootstrap                  boot
    @Lazy
    protected String                     delimiter = getStr('delimiter', '')
    final     List<Consumer<JSONObject>> handlers  = new LinkedList<>()


    TCPClient() { super("tcpClient") }
    TCPClient(String name) { super(name) }


    @EL(name = 'sys.starting')
    def start() {
        create()

        ep.fire("${name}.started")
    }


    @EL(name = 'sys.stopping')
    protected void stop() {
        boos?.shutdownGracefully()
        boot = null
    }


    /**
     * 更新 app 信息
     * @param data app node信息
     *  例: {"name":"rc", "id":"rc_b70d18d5-2269-4512-91ea-6380325e2a84", "tcp":"192.168.56.1:8001","http":"localhost:8000"}
     */
    @EL(name = "updateAppInfo")
    def updateAppInfo(final JSONObject data) {
        log.trace("Update app info: {}", data)
        if (data == null) return
        if (app.id == data["id"]) return // 不把系统本身的信息放进去

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
        if (group == null) throw new RuntimeException("Not found app $appName")
        if ('any' == target) {
            group.sendToAny(data)
        } else if ('all' == target) {
            group.nodes.values().each {if (!it.freeze) it.send(data)}
        } else {
            throw new IllegalArgumentException("Not support target '$target'")
        }
    }


    /**
     * 创建 tcp(netty) 客户端
     */
    protected def create() {
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
                    void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
                        ((Runnable) ctx.channel().attr(AttributeKey.valueOf("removeFn")).get()).run()
                        super.channelUnregistered(ctx)
                    }


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
    protected def receiveReply(ChannelHandlerContext ctx, String dataStr) {
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


    // 应用组
    class AppGroup {
        String name
        // 节点 id -> 节点
        final Map<String, Node> nodes =  new ConcurrentHashMap<>()

        // 更新/添加应用节点
        def updateNode(final JSONObject data) {
            def (String id, tcpHp, httpHp) = [data['id'], data['tcp'], data['http']]
            if (!id || !tcpHp || !httpHp) {
                throw new IllegalArgumentException("Node illegal error. $id, $tcpHp, $httpHp")
            }
            def n = nodes.get(id)
            if (n) { // 更新
                n.tcpHp = tcpHp
                n.httpHp = httpHp
            } else {
                n = new Node(id: id, group: this, httpHp: httpHp, tcpHp: tcpHp)
                nodes.put(id, n)
                log.info("New Node added. appName: " + name + ", nodeId: " + n.id + ", tcpHp: " + n.tcpHp + ", httpHp: " + httpHp)
            }
        }

        // 随机选择一个节点发送数据
        def sendToAny(final String data) {
            if (nodes.size() == 0) {
                throw new RuntimeException("Not found available node for $name")
            } else if (nodes.size()== 1) {
                nodes.find {true}.value.send(data)
            } else if (nodes.size() > 1) {
                Node[] ns = nodes.values().stream().filter{!it.freeze}.toArray()
                if (ns.size() == 0) nodes.iterator().next().value.send(data) // 证明只有一个而且是冻结不可用的
                else if (ns.size() == 1) ns[0].send(data)
                else {
                    def n = ns[new Random().nextInt(ns.length)]
                    n.send(data)
                }
            }
        }


        def setName(String name) {
            if (this.name != null) throw new RuntimeException("'name' not allow change")
            this.name = name
        }
    }


    // 实例节点
    class Node {
        AppGroup          group
        // 冻结(暂不可用)
        boolean freeze
        String            id
        String            tcpHp
        String            httpHp
        final ChannelPool tcpPool = new ChannelPool()

        def setTcpHp(String hp) {
            if (tcpHp == null) {
                this.tcpHp = hp
                def arr = hp.split(":")
                tcpPool.host = arr[0].trim()
                tcpPool.port = Integer.valueOf(arr[1].trim())
                tcpPool.node = this
                tcpPool.create(false) // 默认创建一个
            } else if (tcpHp != hp) {
                // TODO 更新
                throw new RuntimeException("tcpHp not match")
            }
        }

        // 发送数据
        def send(final String data, Consumer<Throwable> failFn = null) {
            def ch = tcpPool.get()
            if (ch == null) {
                def ex = new RuntimeException("Not found available connection")
                if (failFn) failFn.accept(ex)
                else throw ex
            }
            ch.writeAndFlush(toByteBuf(data)).addListener{ChannelFuture cf ->
                if (cf.cause() != null) {
                    if (cf.cause() instanceof ClosedChannelException) {
                        tcpPool.remove(ch)
                    }
                    // TODO
                    if (failFn != null) failFn.accept(cf.cause())
                    else {
                        log.error("Send data fail. app: {}, tcpHp: {}. errMsg: " + cf.cause().message, group?.name, tcpHp)
                    }
                }
            }
        }

        def setId(String id) {
            if (this.id != null) throw new RuntimeException("'id' not Allow change")
            this.id = id
        }

        @Override
        String toString() {
            (id ? "id: $id, " : '') + ("freeze: $freeze, ") + ("tcpHp: $tcpHp, ") + (httpHp ? "httpHp: $httpHp, " : '') + ("poolSize: ${tcpPool.chs.size()}")
        }
    }


    // netty Channel 连接池
    protected class ChannelPool {
              Node          node
              String        host
              Integer       port
        final ReadWriteLock rwLock = new ReentrantReadWriteLock()
        final List<Channel> chs = new ArrayList<>(7)
              AtomicBoolean redeem = new AtomicBoolean(false)

        // 获取连接Channel
        def get(boolean sync = true) {
            if (chs.size() == 0) create(sync)
            if (!sync) return null // 非同步,则立即返回
            if (chs.size() == 0) {
                // TODO
                throw new RuntimeException("Not found available Channel for ${node.group ? node.group.name : node.tcpHp}")
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
        def create(boolean sync = true) {
            try {
                rwLock.writeLock().lock()
                def f = boot.connect(host, port)
                if (sync) { // 同步获取
                    def ch = f.sync().channel()
                    ch.attr(AttributeKey.valueOf("removeFn")).set({remove(ch)} as Runnable)
                    chs.add(ch)
                    node.freeze = false
                } else {
                    f.addListener{ChannelFuture cf ->
                        if (cf.cause() == null) {
                            def ch = cf.channel()
                            ch.attr(AttributeKey.valueOf("removeFn")).set({remove(ch)} as Runnable)
                            chs.add(ch)
                            node.freeze = false
                            log.info("New TCP Connection to '{}'[{}]. total count: " + chs.size(), (node.group?.name?:''), (host+":"+port))
                        }
                    }
                }
            } catch (Throwable th) {
                if (!node.freeze) {
                    log.error("连接失败. '{}:{}', errMsg: " + th.message, host, port)
                }
                exec.execute{redeem()}
            } finally {
                rwLock.writeLock().unlock()
            }
        }

        // 删除 连接Channel
        def remove(final Channel ch) {
            try {
                rwLock.writeLock().lock()
                for (def it = chs.iterator(); it.hasNext(); ) {
                    if (it.next() == ch) {
                        log.info("Remove TCP connection '{}'[{}]. left count: " + chs.size(), (node.group?.name?:''), node.tcpHp)
                        it.remove(); break
                    }
                }
            } finally {
                rwLock.writeLock().unlock()
            }
        }

        // 尝试挽回
        def redeem() {
            if (!redeem.compareAndSet(false, true)) return
            node.freeze = true

            LinkedList<Integer> pass = new LinkedList<>()
            for (String s : getStr("redeemFrequency", "10, 10, 20, 10, 30, 60, 90, 120, 90, 120, 90, 90, 120, 300, 120, 600, 300").split(",")) {
                try {
                    pass.add(Integer.valueOf(s.trim()))
                } catch (Exception ex) {
                    log.warn("Config error property '{}'. {}", name + ".redeemFrequency", ex.message)
                }
            }
            Runnable fn = new Runnable() {
                @Override
                void run() {
                    try {
                        create()
                        if (chs.size() > 0) {
                            node.freeze = false
                            redeem.set(false)
                            log.info("node '{}' redeem success", node)
                        }
                    } catch (Throwable e) {
                        if (pass.isEmpty()) {
                            if (node.id) node.group?.nodes?.remove(node.id)
                            else hpNodes.remove(node.tcpHp)
                            log.warn("node '{}' can't redeem. removed", node)
                        } else {
                            log.warn("node '{}' try redeem fail", node)
                            sched?.after(Duration.ofSeconds(pass.removeFirst()), this)
                        }
                    }
                }
            }
            sched?.after(Duration.ofSeconds(pass.removeFirst()), fn)
        }
    }
}

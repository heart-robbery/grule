package core.module.remote

import cn.xnatural.enet.event.EL
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONException
import com.alibaba.fastjson.JSONObject
import com.alibaba.fastjson.parser.Feature
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

import java.nio.channels.ClosedChannelException
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Consumer

import static core.Utils.linux

/**
 * TCP client
 */
class TCPClient extends ServerTpl {
    @Lazy def remoter = bean(Remoter)
    final     Map<String, Node>          hpNodes   = new ConcurrentHashMap<>()
    final     Map<String, AppGroup>      apps      = new ConcurrentHashMap<>()
    protected EventLoopGroup             boos
    protected Bootstrap                  boot
    @Lazy String                         delimiter = getStr('delimiter', '')
    final     List<Consumer<JSONObject>> handlers  = new LinkedList<>()


    TCPClient() { super("tcpClient") }
    TCPClient(String name) { super(name) }


    @EL(name = 'sys.starting', async = true)
    def start() {
        create()
        ep.fire("${name}.started")
    }


    @EL(name = 'sys.stopping')
    def stop() {
        boos?.shutdownGracefully()
        boot = null
    }


    /**
     * 更新 app 信息
     * @param data app node信息
         *  例: {"name":"rc", "id":"rc_b70d18d52269451291ea6380325e2a84", "tcp":"192.168.56.1:8001","http":"localhost:8000"}
     *  属性不为空: name, id, tcp
     */
    @EL(name = "updateAppInfo", async = false)
    def updateAppInfo(final JSONObject data) {
        if (!data || !data["name"] || !data['id'] || !data['tcp']) {
            log.warn("App up data incomplete: " + data)
            return
        }
        log.trace("Update app info: {}", data)
        if (app.id == data["id"] || remoter?.selfInfo?['tcp'] == data['tcp']) return // 不把系统本身的信息放进去

        String n = data['name']
        def g = apps.get(n)
        if (g == null) {
            synchronized (this) {
                g = apps.get(n)
                if (g == null) {
                    log.info("New app group '{}'", n)
                    g = new AppGroup(n)
                    apps.put(n, g)
                }
            }
        }
        g.updateNode(data)
    }


    /**
     * 发送数据到host:port
     * @param host 主机地址
     * @param port 端口
     * @param data 要发送的数据
     * @param failFn 失败回调函数(可不传)
     */
    void send(String host, Integer port, String data, Consumer<Throwable> failFn = null) {
        log.trace("Send data to '{}:{}'. data: " + data, host, port)
        hpNodes.computeIfAbsent("$host:$port", {hp ->
            def n = new Node(tcpHp: hp)
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
    void send(String appName, String data, String target = 'any') {
        log.trace("Send data to '{}'. data: " + data, appName)
        def group = apps.get(appName)
        if (group == null) throw new RuntimeException("Not found app '$appName' system online")
        if ('any' == target) {
            group.sendToAny(data)
        } else if ('all' == target) {
            group.sendToAll(data)
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
                            void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                ((Runnable) ctx.channel().attr(AttributeKey.valueOf("removeFn")).get())?.run()
                                super.channelInactive(ctx)
                            }

                            @Override
                            void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                log.error("client side error", cause)
                            }

                            @Override
                            void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                                ctx.flush()
                                super.channelReadComplete(ctx)
                            }

                            @Override
                            void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                ByteBuf buf = (ByteBuf) msg
                                String str = null
                                try {
                                    str = buf.toString(Charset.forName("utf-8"))
                                    receiveReply(ctx, str)
                                    ctx.flush()
                                } catch (JSONException ex) {
                                    log.error("Received Error Data from '{}'. data: {}, errMsg: " + ex.message, ctx.channel().remoteAddress(), str)
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
    protected receiveReply(ChannelHandlerContext ctx, final String dataStr) {
        log.trace("Receive reply from '{}': {}", ctx.channel().remoteAddress(), dataStr)
        def jo = JSON.parseObject(dataStr, Feature.OrderedField)
        handlers.each {it.accept(jo)}
        if ("updateAppInfo" == jo['type']) {
            queue() {
                ep.fire("updateAppInfo", jo.getJSONObject("data"))
            }
        }
        else if ("event"== jo['type']) {
            async { remoter?.receiveEventResp(jo.getJSONObject("data")) }
        }
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
        def vs = apps.get(hostname)?.nodes
        if (vs) {
            def ip = (vs.size() == 1 ? (vs[0].tcpHp?.split(":")?[0]) : (vs[new Random().nextInt(vs.size())].tcpHp?.split(":")?[0]))
            if (ip) {
                if ("localhost" == ip) ip = "127.0.0.1"
                return InetAddress.getByAddress(hostname, IPAddressUtil.textToNumericFormatV4(ip))
            }
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
        def vs = apps.get(appName)?.nodes?.values()
        if (vs) {
            if (vs.size() == 1) return vs[0].httpHp
            else {
                return vs[new Random().nextInt(vs.size())].httpHp
            }
        }
        null
    }


    /**
     * 应用组: 一组name相同的应用实例
     */
    class AppGroup {
        // 组名
        final String name
        // 节点id -> 节点
        final Map<String, Node> nodes = new ConcurrentHashMap<>()

        AppGroup(String name) {
            if (!name) throw new IllegalArgumentException("name must not be empty")
            this.name = name
        }

        /**
         * 更新/添加应用节点
         * @param data 应用节点信息
         */
        def updateNode(final JSONObject data) {
            def (String id, String tcpHp, String httpHp, Long uptime) = [data['id'], data['tcp'], data['http'], data['_uptime']]
            if (!tcpHp) { // tcpHp 不能为空
                throw new IllegalArgumentException("Node illegal error. $data")
            }
            def n = nodes.get(id)
            if (n) { // 更新,有可能节点ip变了
                n.uptime = uptime; n.tcpHp = tcpHp.trim(); n.httpHp = httpHp.trim()
            } else {
                n = nodes.values().find {it.tcpHp == tcpHp}
                if (n && n.uptime < uptime) { // tcp host:port 相同, 认为是节点被重启了
                    log.info("Node id update: ('{}' -> '{}')", n.id, id)
                    nodes.put(id, n); nodes.remove(n.id)
                    n.id = id; n.httpHp = httpHp; n.uptime = uptime
                } else {
                    n = new Node(group: this, id: id, tcpHp: tcpHp, httpHp: httpHp, uptime: uptime)
                    nodes.put(id, n)
                    log.info("New Node added. Node: '{}'", n)
                }
            }
        }

        /**
         * 发送给所有节点
         * @param data
         */
        def sendToAll(final String data) {
            nodes.values().each {
                try {
                    it.send(data)
                } catch (ex) {
                    log.error("Send data: '{}' fail. Node: '{}'. errMsg: " + (ex.message?:ex.class.simpleName), data, it)
                }
            }
        }

        /**
         * 随机选择一个节点发送数据
         * @param data
         */
        def sendToAny(final String data) {
            final List<Node> ns = new LinkedList<>()
            nodes.each {ns.add(it.value)}
            randomStrategy(ns, data)
        }

        /**
         * 随机节点策略
         * @param ns 节点列表
         * @param data 要发送的数据
         */
        void randomStrategy(List<Node> ns, String data) {
            if (!ns) throw new RuntimeException("Not found available node for '$name'")
            if (ns.size() == 1) ns[0].send(data)
            else {
                def n = ns[new Random().nextInt(ns.size())]
                n.send(data, {ex -> // 尽可能找到可用的节点把数据发送出去
                    log.error(ex.class.simpleName+": " +ex.message)
                    ns.remove(n)
                    randomStrategy(ns, data)
                })
            }
        }
    }


    /**
     * 实例节点
     */
    protected class Node {
        // 应用实例所属组
        AppGroup          group
        // 应用实例id
        String            id
        // 应用实例暴露的tcp连接信息: host:port
        String            tcpHp
        // 应用实例暴露的http连接信息: host:port
        String            httpHp
        // 上次更新时间
        Long              uptime
        // tcp 连接池
        final ChannelPool tcpPool = new ChannelPool()

        def setTcpHp(String hp) {
            if (!hp) throw new IllegalArgumentException("Node tcpHp must not be empty")
            if (tcpHp == null) {
                try {
                    this.tcpHp = hp
                    def arr = hp.split(":")
                    tcpPool.host = arr[0].trim()
                    tcpPool.port = Integer.valueOf(arr[1].trim())
                    tcpPool.node = this
                } catch (ex) {
                    throw new RuntimeException("tcp hp: '$hp' 格式信息错误. " + ex.message)
                }
                // async {tcpPool.create()} // 默认创建一个
            }
            else if (tcpHp != hp) { // 更新tcp连接信息(有可能ip变了)
                try {
                    tcpPool.rwLock.writeLock().lock()
                    tcpPool.chs.each {
                        try {it.close()} catch (ex) {}
                    }
                    tcpPool.chs.clear()
                    log.info("Node tcp update: ('{}' -> '{}')", this.tcpHp, hp)
                    this.tcpHp = hp
                    def arr = hp.split(":")
                    tcpPool.host = arr[0].trim()
                    tcpPool.port = Integer.valueOf(arr[1].trim())
                    tcpPool.node = this
                } catch (ex) {
                    throw new RuntimeException("tcp hp: '$hp' 格式信息错误. " + ex.message)
                } finally {
                    tcpPool.rwLock.writeLock().unlock()
                }
                // async {tcpPool.create()} // 默认创建一个
            }
        }

        /**
         * 发送数据
         * @param data 数据
         * @param failFn 失败函数
         * @param limit 重试次数
         */
        void send(final String data, Consumer<Throwable> failFn = null, AtomicInteger limit = new AtomicInteger(0)) {
            def ch = null
            try {
                ch = tcpPool.get()
                if (limit.get() == 0) ((Runnable) ch.attr(AttributeKey.valueOf("upFn")).get())?.run()
                ch.writeAndFlush(toByteBuf(data)).sync()
            } catch (Throwable ex) {
                if (ex instanceof ClosedChannelException) { // 通道关闭的情况,可重试
                    tcpPool.remove(ch)
                    if (limit.get() < 2) {
                        limit.incrementAndGet()
                        send(data, failFn, limit) // 重试
                        return
                    }
                }
                if (failFn) failFn.accept(ex)
                else throw ex
            }
        }

        @Override
        String toString() {
            '[' + (group ? "name:$group.name, " : '') + (id ? "id:$id, " : '') + ("tcpHp:$tcpHp, ") + (httpHp ? "httpHp:$httpHp, " : '') + ("tcpPoolSize:${tcpPool.chs.size()}") + ']'
        }
    }


    /**
     * netty Channel 连接池
     */
    protected class ChannelPool {
              Node          node
              String        host // TODO 域名 对应多个ip
              Integer       port
        final ReadWriteLock rwLock = new ReentrantReadWriteLock()
        final List<Channel> chs    = new ArrayList<>(7)

        /**
         * 获取连接Channel
         * @return {@link Channel}
         */
        Channel get() {
            if (chs.size() == 0) add()
            if (chs.size() == 0) {
                throw new RuntimeException("Not found available Channel for '${node.toString()}'")
            }
            Channel ch
            try {
                rwLock.readLock().lock()
                if (chs.size() == 1) {
                    ch = chs.get(0)
                } else {
                    ch = chs.get(new Random().nextInt(chs.size())) // 随机选择连接
                }
            } finally {
                rwLock.readLock().unlock()
            }

            // 验证是否可用
            if (ch && ch.isActive()) return ch
            remove(ch)
            return get()
        }

        /**
         * 添加新连接Channel
         */
        void add() {
            Channel ch
            try { // 连接
                ch = boot.connect(host, port).sync().channel()
                initChannel(ch)
            } catch (Throwable th) {
                if (node.group && node.group.nodes.size() > 1) { // 删除连接不上的节点, 但保留一个
                    node.group.nodes.remove(node.id)
                }
                throw new RuntimeException("Create TCP Connection error. node: '${node}'. errMsg: $th.class.simpleName: $th.message", th)
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
        }

        // 初始化连接Channel
        protected initChannel(final Channel ch) {
            ch.attr(AttributeKey.valueOf("removeFn")).set({ remove(ch) } as Runnable)

            // 1分钟的调用记录时间
            final Queue<Long> records = new ConcurrentLinkedQueue<>()
            ch.attr(AttributeKey.valueOf("upFn")).set({
                records.offer(System.currentTimeMillis())
                while (true) {
                    def t = records.peek()
                    if (t && System.currentTimeMillis() - t > 60 * 1000L) records.poll()
                    else break
                }
                if (records.size() > getInteger("oneMinuteLimitPerChannel", 70) && chs.size() < getInteger('maxConnectPerNode', 3)) {
                    async {add()}
                }
            } as Runnable)
        }

        // 删除连接Channel
        void remove(final Channel ch) {
            if (ch == null) return
            try {
                rwLock.writeLock().lock()
                for (def it = chs.iterator(); it.hasNext();) {
                    if (it.next() == ch) {
                        it.remove()
                        try {ch.close()} catch (ex) {}
                        log.info("Remove TCP connection for Node '{}'", node)
                        break
                    }
                }
            } finally {
                rwLock.writeLock().unlock()
            }
        }

        def setPort(Integer port) {
            if (port == null) throw new IllegalArgumentException("端口号为空")
            if (port < 0 && port > 65535) throw new IllegalArgumentException("端口号范围错误. port: " + port)
            this.port = port
        }
    }
}
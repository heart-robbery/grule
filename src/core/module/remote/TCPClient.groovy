package core.module.remote

import cn.xnatural.enet.event.EL
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONException
import com.alibaba.fastjson.JSONObject
import core.AppContext
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
import org.h2.server.TcpServer
import org.slf4j.event.Level

import javax.annotation.Resource
import java.nio.charset.Charset
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Consumer
import java.util.stream.Collectors

import static core.Utils.linux
import static java.util.concurrent.TimeUnit.SECONDS

class TCPClient extends ServerTpl {
    @Resource
    protected       Remoter               remoter
    @Resource
    protected       Executor              exec
    @Resource
    protected       TcpServer             tcpServer
    protected final Map<String, Node>     hpNodes   = new ConcurrentHashMap<>()
    protected final Map<String, AppGroup> apps      = new ConcurrentHashMap<>()
    protected       EventLoopGroup        boos
    protected       Bootstrap             boot
    @Lazy
    final           String                delimiter = getStr('delimiter', '')
    final List<Consumer<JSONObject>> handlers = new LinkedList<>()


    TCPClient() { super("tcp-client") }
    TCPClient(String name) { super(name) }


    @EL(name = 'sys.starting')
    def start() {
        create()
        handlers.add{jo ->
            if ("updateAppInfo" == jo['type']) {
                exec.execute(() -> {
                    JSONObject d
                    try { d = jo.getJSONObject("data"); updateAppInfo(d) }
                    catch (Exception ex) {
                        log.error("updateAppInfo error. data: " + d, ex)
                    }
                })
            }
        }
        ep.fire("${name}.started")
    }


    @EL(name = 'sys.stopping')
    protected void stop() {
        boos?.shutdownGracefully()
        boot = null
        apps.clear()
    }


    @EL(name = "sys.started")
    protected void sysStarted() {
        // 注册自己到自己
        if (getBoolean("registerSelf", true)) {
            tcpServer?.upDevourer.offer{
                JSONObject d
                try { d = jo.getJSONObject("data"); appUp(d, ctx) }
                catch (Exception ex) {
                    log.error("Register up error!. data: " + d, ex)
                }
            }
            synchronized (tcpServer.appInfoMap) {
                JSONObject d = collectData();
                if (isNotEmpty(d)) {
                    d.put("_time", System.currentTimeMillis());
                    remoter.tcpServer.appInfoMap.computeIfAbsent(remoter.sysName, s -> new LinkedList<>()).add(d);
                }
            }
        }
        register()
    }


    /**
     * 向注册服务注册自己
     */
    protected void register() {
        Runnable loopFn = new Runnable() {
            boolean printInfo = true // 是否用 INFO日志等级打印注册成功信息
            @Override
            void run() {
                AppInfo ai = appInfoMap.get(masterAppName); // 查找自己的注册中心服务
                if (ai == null) return;
                JSONObject data = collectData();
                if (isEmpty(data)) return;
                // 异常处理
                Consumer<Throwable> exHandler = ex -> {
                    log.warn("Register Fail to rc server '{}'. errMsg: {}", ai.name, (isEmpty(ex.getMessage()) ? ex.getClass().getName() : ex.getMessage()));
                    printInfo = true; // 发送失败后的下一次成功,打印发送成功的日志信息
                    ep.fire("sched.after", Duration.ofSeconds(new Random().nextInt(getInteger("upFailIntervalRange", 30)) + getInteger("upFailIntervalMin", 30)), this);
                };
                try {
                    // 向注册中心发送注册信息(本系统信息)
                    send(ai.name, new JSONObject(3).fluentPut("type", "up").fluentPut("source", sysName).fluentPut("data", data), ex -> {
                        if (ex != null) { exHandler.accept(ex); } // 发送失败
                        else {
                            log.doLog((printInfo ? Level.INFO : Level.DEBUG), null, "Register up self '{}' to '{}'. info: {}", sysName, ai.name + ai.hps, data);
                            printInfo = false;
                        }
                    });
                    // 每隔一段时间,都去注册下自己
                    ep.fire("sched.after", (Duration.ofSeconds(getInteger("upInterval", new Random().nextInt(getInteger("upIntervalRange", 100)) + getInteger("upIntervalMin", 60)))), this);
                } catch (Throwable ex) { exHandler.accept(ex); }
            }
        };
        loopFn.run();
    }


    /**
     * 更新 app 信息
     * @param data app信息.例: {"name":"rc", "id":"rc_b70d18d5-2269-4512-91ea-6380325e2a84", "tcp":"192.168.56.1:8001","http":"localhost:8000"}
     */
    @EL(name = "updateAppInfo")
    def updateAppInfo(final JSONObject data) {
        log.trace("Update app info: {}", data)
        if (data == null) return
        if (app.id == data["id"]) return // 不把系统本身的信息放进去

        apps.computeIfAbsent(data["name"], {s -> new AppGroup(name: s)})
                .setNode(data['id'], data['tcp'], data['http'])
    }



    /**
     * 发送数据到host:port
     * @param host 主机地址
     * @param port 端口
     * @param data 要发送的数据
     */
    def send(String host, Integer port, String data) {
        log.debug("Send data to '{}:{}'. data: " + data, host, port)
        hpNodes.computeIfAbsent("$host+':'+$port", {s -> new Node(tcpHp: s)}).send(data)
    }



    /**
     * 向app发送数据
     * @param appName 应用名
     * @param data 要发送的数据
     */
    def send(String appName, String data) {
        def group = apps.get(appName)
        if (group == null) throw new RuntimeException("Not found app $appName")
        group.sendToAny(data)
    }


    /**
     * 发送数据 到 app
     * @param appName 向哪个应用发数据
     * @param data 要发送的数据
     * @param completeFn 发送完成后回调函数
     */
    def send(String appName, String data, Consumer<Throwable> completeFn) {
        log.trace("Send data to app '{}'. data: {}", appName, data)
        Channel ch = channel(appName)
        ch.writeAndFlush(remoter.toByteBuf(data)).addListener(f -> {
            exec.execute(() -> {
                LinkedList<Long> record = appInfoMap.get(appName).hpErrorRecord.get(ch.attr(AttributeKey.valueOf("hp")).get());
                if (f.isSuccess() && !record.isEmpty()) record.clear();
                else if (f.cause() != null) record.addFirst(System.currentTimeMillis())
                if (completeFn != null) completeFn.accept(f.cause())
            })
        })
    }


    /**
     * 得到一个指向 appName 的Channel
     * @param appName
     * @return
     */
    protected Channel channel(String appName) {
        // 一个 app 可能被部署多个实例, 每个实例可以创建多个连接
        AppInfo info = appInfoMap.get(appName);
        if (info == null) {
            throw new IllegalArgumentException("Not found available server for '" + appName + "'");
        }
        if (info.chs.isEmpty()) adaptChannel(info, true);

        // 获取一个连接Channel
        Channel ch;
        try {
            info.rwLock.readLock().lock();
            if (info.chs.isEmpty()) throw new IllegalArgumentException("Not found available channel for '" + appName +"'");
            else if (info.chs.size() == 1) ch = info.chs.get(0);
            else { ch = info.chs.get(new Random().nextInt(info.chs.size())); }
        } finally {
            info.rwLock.readLock().unlock();
        }
        return ch;
    }


    /**
     * 为每个连接配置(hp:(host:port)) 适配 连接
     * @param appInfo 应用名
     * @param once 是否创建一条连接就立即返回
     */
    protected void adaptChannel(AppInfo appInfo, boolean once) {
        if (isEmpty(appInfo.hps)) {
            throw new IllegalArgumentException("Not found connection config for '"+ appInfo.name +"'");
        }
        if (once && appInfo.chs.size() > 0) return;
        Integer maxConnectionPerHp = getInteger(appInfo.name + ".maxConnectionPerHp", getInteger("maxConnectionPerHp", 2)); // 每个hp(host:port)最多可以建立多少个连接
        if (appInfo.chs.size() >= (appInfo.hps.size() * maxConnectionPerHp)) return; // 已满
        try {
            appInfo.rwLock.writeLock().lock();
            for (Iterator<String> it = appInfo.hps.iterator(); it.hasNext(); ) {
                String hp = it.next();
                if (isBlank(hp) || !hp.contains(":")) {
                    log.warn("Config error {}", appInfo.hps); continue;
                }
                AtomicInteger count = appInfo.hpChannelCount.computeIfAbsent(hp, s -> new AtomicInteger(0));
                if (count.get() >= maxConnectionPerHp) continue; // 连接个数据大于等于最大连接数,就不创建新连接了
                LinkedList<Long> errRecord = appInfo.hpErrorRecord.computeIfAbsent(hp, s -> new LinkedList<>());
                Long lastRefuse = errRecord.peekFirst(); // 上次被连接被拒时间
                if (!once && lastRefuse != null && (System.currentTimeMillis() - lastRefuse < 2000)) { // 距上次连接被拒还没超过2秒, 则不必再连接
                    continue;
                }
                String[] arr = hp.split(":");
                try {
                    ChannelFuture f = boot.connect(arr[0], Integer.valueOf(arr[1]));
                    f.sync().await(getInteger("connectTimeout", 3), SECONDS);
                    Channel ch = f.channel();
                    ch.attr(AttributeKey.valueOf("app")).set(appInfo); ch.attr(AttributeKey.valueOf("hp")).set(hp);
                    appInfo.chs.add(ch); count.incrementAndGet();
                    log.info("New TCP Connection to '{}'[{}]. total count: {}", appInfo.name, hp, count);
                    if (once) break;
                } catch (Exception ex) {
                    errRecord.addFirst(System.currentTimeMillis());
                    // 多个连接配置, 当某个连接(hp(host:port))多次发生错误, 则移除此条连接配置
                    if (appInfo.hps.size() > 1) { it.remove(); redeem(appInfo, hp); }
                    log.doLog((appInfo.hps.size() > 1 ? Level.WARN : Level.ERROR), null, "Connect Error to '{}'[{}]. errMsg: {}", appInfo.name, hp, (isEmpty(ex.getMessage()) ? ex.getClass().getName() : ex.getMessage()));
                }
            }
        } finally {
            appInfo.rwLock.writeLock().unlock();
        }

        // 立即返回, 已有成功的连接, 少于最多连接
        if (once && appInfo.chs.size() > 0 && (appInfo.hps.size() * maxConnectionPerHp) > appInfo.chs.size()) {
            exec.execute(() -> adaptChannel(appInfo, false));
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
                .option(ChannelOption.SO_TIMEOUT, getInteger("soTimeout", 10000))
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        // ch.pipeline().addLast(new IdleStateHandler(getLong("readerIdleTime", 12 * 60 * 60L), getLong("writerIdleTime", 10L), getLong("allIdleTime", 0L), SECONDS))
                        ch.pipeline().addLast(new DelimiterBasedFrameDecoder(getInteger("maxFrameLength", 1024 * 1024), Unpooled.copiedBuffer(delimiter.getBytes("utf-8"))))
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
                                removeChannel(ch)
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
    protected void receiveReply(ChannelHandlerContext ctx, String dataStr) {
        log.trace("Receive reply from '{}': {}", ctx.channel().remoteAddress(), dataStr)
        handlers.each {it.accept(JSON.parseObject(dataStr))}
    }


    /**
     * 删除连接
     * @param ch
     */
    protected void removeChannel(Channel ch) {
        AppInfo app = (AppInfo) ch.attr(AttributeKey.valueOf("app")).get()
        if (app == null) return
        String hp = (String) ch.attr(AttributeKey.valueOf("hp")).get()
        try {
            app.rwLock.writeLock().lock()
            for (Iterator<Channel> it = app.chs.iterator(); it.hasNext(); ) {
                if (it.next()== ch) {it.remove(); break}
            }
        } finally {
            app.rwLock.writeLock().unlock()
        }
        AtomicInteger count = app.hpChannelCount.get(hp); count.decrementAndGet()
        log.info("Remove TCP Connection to '{}'[{}]. left count: {}", app.name, hp, count)
    }


    /**
     * 尝试挽回 被删除 连接配置
     * @param appInfo
     * @param hp
     */
    protected void redeem(AppInfo appInfo, String hp) {
        LinkedList<Integer> pass = new LinkedList<>()
        for (String s : getStr("redeemFrequency", "5, 10, 5, 10, 20, 35, 40, 45, 50, 60, 90, 120, 90, 120, 90, 60, 90, 60, 90, 60, 90, 120, 300, 120, 600, 300").split(",")) {
            try {
                pass.add(Integer.valueOf(s.trim()));
            } catch (Exception ex) {
                log.warn("Config error property '{}'. {}", getName() + ".redeemFrequency", ex.getMessage());
            }
        }
        Runnable fn = new Runnable() {
            @Override
            public void run() {
                String[] arr = hp.split(":");
                try {
                    ChannelFuture f = boot.connect(arr[0], Integer.valueOf(arr[1]));
                    f.sync().await(getInteger("connectTimeout", 3), SECONDS);
                    f.channel().disconnect();
                    try {
                        appInfo.rwLock.writeLock().lock();
                        appInfo.hps.add(hp);
                        log.info("'{}'[{}] redeem success", appInfo.name, hp); appInfo.hpErrorRecord.get(hp).clear();
                    } finally {
                        appInfo.rwLock.writeLock().unlock();
                    }
                } catch (Exception e) {
                    if (pass.isEmpty()) { log.warn("'{}'[{}] can't redeem", appInfo.name, hp); appInfo.hpErrorRecord.get(hp).removeLast(); }
                    else {
                        if (appInfo.hps.contains(hp)) {// 又被加入到配置里面去了,停止redeem
                            log.info("Stop redeem '{}'[{}]", appInfo.name, hp); appInfo.hpErrorRecord.get(hp).removeLast();
                        } else {
                            log.warn("'{}'[{}] try redeem fail", appInfo.name, hp);
                            ep.fire("sched.after", Duration.ofSeconds(pass.removeFirst()), this);
                        }
                    }
                }
            }
        };
        ep.fire("sched.after", Duration.ofSeconds(pass.removeFirst()), fn);
    }



    // 字符串 转换成 ByteBuf
    ByteBuf toByteBuf(String data) { Unpooled.copiedBuffer((data + (delimiter?:'')).getBytes('utf-8')) }


    // 应用组
    protected class AppGroup {
        String name
        final Map<String, Node> nodes =  new ConcurrentHashMap<>()

        // 添加/更新应用节点
        def setNode(String id, String tcpHp, String httpHp) {
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
            }
        }

        // 随机选择一个节点发送数据
        def sendToAny(String data) {
            if (nodes.size() == 0) {
                throw new RuntimeException("Not found any node for $name")
            } else if (nodes.size()== 1) {
                nodes.find {true}.value.send(data)
            } else if (nodes.size() > 1) {
                String[] arr = nodes.keySet().toArray()
                nodes.get(arr[new Random().nextInt(arr.length)]).send(data)
            }
        }
    }


    // 实例节点
    protected class Node {
        AppGroup          group
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
            } else {
                // TODO
            }
        }

        // 发送数据
        def send(String data) {
            def ch = tcpPool.get()
            ch.writeAndFlush(toByteBuf(data)).addListener{ChannelFuture cf ->
                if (cf.cause() != null) {
                    // TODO
                    log.error("Send data fail. app: {}, tcpHp: {}. errMsg: " + cf.cause().message, group?.name, tcpHp)
                }
            }
        }
    }


    // netty Channel 连接池
    protected class ChannelPool {
        Node node
        String host
        Integer port
        final ReadWriteLock rwLock = new ReentrantReadWriteLock()
        final List<Channel> chs = new ArrayList<>(7)

        // 获取连接Channel
        def get(boolean sync = true) {
            if (chs.size() == 0) create(sync)
            if (!sync) return null// 非同步,则立即返回
            if (chs.size() == 0) {
                // TODO
                throw new RuntimeException("Not found available Channel for ${node?.group?.name}, $node.id, ${host+':'+port}")
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
        }

        // 创建新连接Channel
        def create(boolean sync = true) {
            try {
                rwLock.writeLock().lock()
                def f = boot.connect(host, port)
                if (sync) { // 同步获取
                    def ch = f.sync().channel()
                    ch.attr(AttributeKey.valueOf("app"), node.group.name)
                    ch.attr(AttributeKey.valueOf("id"), node.id)
                    chs.add(ch)
                } else {
                    f.addListener{ChannelFuture cf ->
                        if (cf.cause() == null) {
                            def ch = cf.channel()
                            ch.attr(AttributeKey.valueOf("app"), node.group.name)
                            ch.attr(AttributeKey.valueOf("id"), node.id)
                            chs.add(ch)
                            log.info("New TCP Connection to '{}'[{}]. total count: " + chs.size(), node.group.name, (host+":"+port))
                        }
                        else log.error("连接失败. '{}:{}', errMsg: " + cf.cause().message, host, port)
                    }
                }
            } finally {
                rwLock.writeLock().unlock()
            }
        }

        // 删除 连接Channel
        def remove(Channel ch) {
            try {
                rwLock.writeLock().lock()
                for (def it = chs.iterator(); it.hasNext(); ) {
                    if (it.next() == ch) {
                        it.remove(); break
                    }
                }
            } finally {
                rwLock.writeLock().unlock()
            }
        }
    }


    protected class AppInfo {
        protected String                        name
        /**
         * app 连接配置信息
         * hp(host:port)
         */
        protected Set<String>                   hps            = ConcurrentHashMap.newKeySet()
        /**
         * {@link #chs} 读写锁
         */
        protected ReadWriteLock rwLock         = new ReentrantReadWriteLock()
        /**
         * list Channel
         */
        protected List<Channel>                 chs            = new ArrayList(7)
        /**
         * hp(host:port) -> list Channel
         */
        protected Map<String, AtomicInteger>    hpChannelCount = new ConcurrentHashMap<>()
        /**
         * hp(host:port) -> 连接异常时间
         */
        protected Map<String, LinkedList<Long>> hpErrorRecord  = new ConcurrentHashMap<>()

        AppInfo(String name) {
            if (!name) throw new IllegalArgumentException("name must not be empty")
            this.name = name
        }
    }
}

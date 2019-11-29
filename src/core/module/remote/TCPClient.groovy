package core.module.remote

import cn.xnatural.enet.event.EL
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONException
import com.alibaba.fastjson.JSONObject
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
    protected       Executor             exec
    @Resource
    protected       TcpServer   tcpServer
    protected       Map<String, AppInfo> appInfoMap = new ConcurrentHashMap<>()
    protected       EventLoopGroup       boos
    protected       Bootstrap            boot
    /**
     * 服务中心系统名字.
     * 会把 appName指向的系统 当作自己的服务注册服务. 并向 appName指向的系统注册自己
     */
    protected       String               rcAppName
    @Lazy
    final           String                                 delimiter  = getStr('delimiter', '')
    final Map<String, Consumer<String>> completeFnMap = new ConcurrentHashMap<>()


    TCPClient() { super("tcp-client") }


    @EL(name = 'sys.starting')
    def start() {
        rcAppName = getStr("masterAppName", "master")
        // 连接注册中心的的是大连接数:默认1个
        if (!attrs.containsKey(rcAppName + ".maxConnectionPerHp")) attr(rcAppName + ".maxConnectionPerHp", 1)
        attrs.forEach{String k, v ->
            if (k.endsWith(".hosts")) { // 例: app1.hosts=ip1:8201,ip2:8202,ip2:8203
                String appName = k.split("\\.")[0].trim()
                for (String hp : ((String) v).split(",")) {
                    String[] arr = hp.trim().split(":")
                    appInfoMap.computeIfAbsent(appName, (s) -> new AppInfo(s)).hps.add(arr[0].trim() + ":" + Integer.valueOf(arr[1].trim()))
                }
            }
        }
        create()
    }


    @EL(name = 'sys.stopping')
    protected void stop() {
        boos?.shutdownGracefully()
        boot = null
        appInfoMap.clear()
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
        register();
    }


    /**
     * 向注册服务注册自己
     */
    protected void register() {
        Runnable loopFn = new Runnable() {
            boolean printInfo = true; // 是否用 INFO日志等级打印注册成功信息
            @Override
            void run() {
                AppInfo ai = appInfoMap.get(rcAppName); // 查找自己的注册中心服务
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
     * @param data app信息.例: {"tcp":"192.168.56.1:8001","name":"rc","http":"localhost:8000","id":"rc_b70d18d5-2269-4512-91ea-6380325e2a84"}
     */
    @EL(name = "updateAppInfo")
    def updateAppInfo(final JSONObject data) {
        log.trace("Update app info: {}", data);
        if (data == null) return
        if (Objects.equals(id, data.getString("id"))) return; // 不把系统本身的信息放进去
        String appName = data.getString("name");
        AppInfo app = Optional.ofNullable(appInfoMap.get(appName)).orElseGet(() -> {
            AppInfo o;
            synchronized (appInfoMap) {
                o = appInfoMap.get(appName);
                if (o == null) {
                    o = new AppInfo(appName); appInfoMap.put(appName, o);
                }
            }
            return o;
        });

        // 更新tcp连接配置的信息
        String tcpHps = data.getString("tcp");
        if (isNotEmpty(tcpHps)) {
            Set<String> add = Arrays.stream(tcpHps.split(","))
                    .filter(hp -> hp != null).map(hp -> hp.trim())
                    .filter(hp -> !hp.isEmpty() && !app.hps.contains(hp))
                    .filter(hp -> !collectData().getString("tcp").contains(hp)) // 不要和本系统的tcp相同的配置
                    .collect(Collectors.toSet());
            if (!add.isEmpty()) {
                try {
                    app.rwLock.writeLock().lock();
                    app.hps.addAll(add);
                    log.info("New TCP config '{}'{} added", appName, add);
                } finally {
                    app.rwLock.writeLock().unlock();
                }
            }
        }

        // TODO 更新http连接信息
    }


    /**
     * 发送数据到host:port
     * @param host 主机地址
     * @param port 端口
     * @param data 要发送的数据
     */
    def send(String host, Integer port, String data) {
        log.debug("Send data to '{}:{}'. data: " + data, host, port)
        ChannelFuture cf = boot.connect(host, port)
        cf.await(3000L)
        cf.channel().writeAndFlush(toByteBuf(data))
    }


    /**
     * 向app发送数据
     * @param appName 应用名
     * @param data
     */
    def send(String appName, String data) {

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
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new DelimiterBasedFrameDecoder(getInteger("maxFrameLength", 1024 * 1024), Unpooled.copiedBuffer(delimiter.getBytes("utf-8"))));
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
                                    log.error("Received Error Data from '{}'. data: {}, errMsg: {}", ctx.channel().remoteAddress(), str, ex.getMessage());
                                    ctx.close()
                                } finally {
                                    ReferenceCountUtil.release(msg)
                                }
                            }
                        });
                    }
                });
    }


    /**
     * 客户端接收到服务端的响应数据
     * @param ctx
     * @param dataStr
     */
    protected void receiveReply(ChannelHandlerContext ctx, String dataStr) {
        log.trace("Receive reply from '{}': {}", ctx.channel().remoteAddress(), dataStr);
        JSONObject jo = JSON.parseObject(dataStr);
        String type = jo.getString("type");
        if ("event".equals(type)) {
            exec.execute(() -> remoter.receiveEventResp(jo.getJSONObject("data")));
        } else if ("updateAppInfo".equals(type)) {
            exec.execute(() -> {
                JSONObject d = null;
                try { d = jo.getJSONObject("data"); updateAppInfo(d); }
                catch (Exception ex) {
                    log.error(ex, "updateAppInfo error. data: {}", d);
                }
            });
        }
    }


    /**
     * 删除连接
     * @param ch
     */
    protected void removeChannel(Channel ch) {
        AppInfo app = (AppInfo) ch.attr(AttributeKey.valueOf("app")).get();
        if (app == null) return;
        String hp = (String) ch.attr(AttributeKey.valueOf("hp")).get();
        try {
            app.rwLock.writeLock().lock();
            for (Iterator<Channel> it = app.chs.iterator(); it.hasNext(); ) {
                if (it.next().equals(ch)) {it.remove(); break;}
            }
        } finally {
            app.rwLock.writeLock().unlock();
        }
        AtomicInteger count = app.hpChannelCount.get(hp); count.decrementAndGet();
        log.info("Remove TCP Connection to '{}'[{}]. left count: {}", app.name, hp, count);
    }


    /**
     * 尝试挽回 被删除 连接配置
     * @param appInfo
     * @param hp
     */
    protected void redeem(AppInfo appInfo, String hp) {
        LinkedList<Integer> pass = new LinkedList<>();
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


    /**
     * 自己的信息
     * 例: {"id":"rc_b70d18d5-2269-4512-91ea-6380325e2a84", "name":"rc", "tcp":"192.168.56.1:8001", "http":"localhost:8000"}
     */
    def getSelfInfo() {
        JSONObject data = new JSONObject(4)
        data.put("id", app.id)
        data.put("name", app.name)
        Optional.ofNullable(ep.fire("http.getHp")).ifPresent{ data.put("http", it) }
        data.put("tcp", hps)

        if (!data.containsKey('tcp') && !data.containsKey('http')) return null
        return data
    }


    ByteBuf toByteBuf(String data) { Unpooled.copiedBuffer((data + delimiter).getBytes('utf-8')) }




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

        public AppInfo(String name) {
            if (isEmpty(name)) throw new IllegalArgumentException("name must not be empty")
            this.name = name
        }
    }
}

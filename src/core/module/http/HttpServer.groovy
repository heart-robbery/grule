package core.module.http

import cn.xnatural.enet.event.EL
import core.Utils
import core.module.SchedSrv
import core.module.ServerTpl
import core.module.http.mvc.*

import java.lang.reflect.InvocationTargetException
import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.LongAdder
import java.util.function.Consumer

import static core.Utils.ipv4

/**
 * 基于 AIO 的 Http 服务
 */
class HttpServer extends ServerTpl {
    protected final CompletionHandler<AsynchronousSocketChannel, HttpServer> handler = new AcceptHandler()
    protected       AsynchronousServerSocketChannel                          ssc
    private @Lazy           String                                           hpCfg   = getStr('hp', ":9090")
    @Lazy           Integer                                                  port    = hpCfg.split(":")[1] as Integer
    protected final Chain                                                    chain   = new Chain(this)
    protected final List                                                     ctrls   = new LinkedList<>()
    // 是否可用
    boolean                                                                  enabled = false
    @Lazy def                                                                sched   = bean(SchedSrv)


    HttpServer(String name) { super(name) }
    HttpServer() { super('web') }


    @EL(name = 'sys.starting', async = true)
    void start() {
        if (ssc) throw new RuntimeException("$name is already running")
        def cg = AsynchronousChannelGroup.withThreadPool(exec)
        ssc = AsynchronousServerSocketChannel.open(cg)
        ssc.setOption(StandardSocketOptions.SO_REUSEADDR, true)
        ssc.setOption(StandardSocketOptions.SO_RCVBUF, getInteger('so_revbuf', 64 * 1024))

        String host = hpCfg.split(":")[0]
        def addr = new InetSocketAddress(port)
        if (host) {addr = new InetSocketAddress(host, port)}

        ssc.bind(addr, getInteger('backlog', 100))
        initChain()
        log.info("Start listen HTTP(AIO) {}", port)
        accept()
    }


    @EL(name = 'sys.stopping', async = true)
    void stop() { ssc?.close() }


    @EL(name = 'sys.started', async = true)
    protected void started() { ctrls.each {app.inject(it)}; enabled = true }


    /**
     * 接收新的 http 请求
     * @param request
     */
    protected void receive(HttpRequest request) {
        log.info("Start Request '{}': {}. from: " + request.session.sc.remoteAddress.toString(), request.id, request.rowUrl)
        count()
        HttpContext hCtx
        try {
            hCtx = new HttpContext(request, this)
            if (enabled) chain.handle(hCtx)
            else hCtx.render(ApiResp.fail('请稍候...'))
        } catch (ex) {
            log.error("请求处理错误", ex)
            hCtx?.close()
        }
    }


    /**
     * 添加
     * @param clzs
     * @return
     */
    HttpServer ctrls(Class...clzs) {
        clzs?.each {clz -> ctrls.add(clz.newInstance()) }
        this
    }


    /**
     * 初始化Chain
     */
    protected void initChain() {
        ctrls?.each {ctrl ->
            ep.addListenerSource(ctrl)
            def anno = ctrl.class.getAnnotation(Ctrl)
            if (anno) {
                if (anno.prefix()) {
                    chain.prefix(anno.prefix(), {ch -> parseCtrl(ctrl, ch)})
                } else parseCtrl(ctrl, chain)
            } else {
                log.warn("@Ctrl Not Fund in: " + ctrl.class.simpleName)
            }
        }
    }


    /**
     * 解析 @Ctrl 类
     * @param ctrl
     * @param chain
     */
    protected void parseCtrl(Object ctrl, Chain chain) {
        def aCtrl = ctrl.class.getAnnotation(Ctrl)
        Utils.iterateMethod(ctrl.class, { method ->
            def aPath = method.getAnnotation(Path)
            if (aPath) { // 路径映射
                if (!aPath.path()) {
                    log.error("@Path path must not be empty. {}#{}", ctrl.class.simpleName, method.name)
                    return
                }
                log.info("Request mapping: /" + ((aCtrl.prefix() ? aCtrl.prefix() + "/" : '') + aPath.path()))
                def ps = method.getParameters(); method.setAccessible(true)
                chain.method(aPath.method(), aPath.path()) {HttpContext hCtx -> // 实际@Path 方法 调用
                    try {
                        def result = method.invoke(ctrl,
                            ps.collect {p ->
                                if (HttpContext.isAssignableFrom(p.type)) return hCtx
                                hCtx.param(p.name, p.type)
                            }.toArray()
                        )
                        if (!void.class.isAssignableFrom(method.returnType)) {
                            log.debug("Invoke Handler '" + (ctrl.class.simpleName + '#' + method.name) + "', result: " + result)
                            hCtx.render(result)
                        }
                    } catch (InvocationTargetException ex) {
                        throw ex.cause
                    }
                }
                return
            }
            def aFilter = method.getAnnotation(Filter)
            if (aFilter) { // Filter处理
                if (!void.class.isAssignableFrom(method.returnType)) {
                    log.error("@Filter return type must be void. {}#{}", ctrl.class.simpleName, method.name)
                    return
                }
                log.info("Request filter: /" + (aCtrl.prefix()))
                def ps = method.getParameters(); method.setAccessible(true)
                chain.all({HttpContext ctx -> // 实际@Filter 方法 调用
                    method.invoke(ctrl,
                        ps.collect {p ->
                            if (p instanceof HttpContext) return ctx
                            ctx.param(p.name, p.type)
                        }.toArray()
                    )
                })
                return
            }
        })
    }


    /**
     * 手动构建 Chain
     * @param buildFn
     * @return
     */
    HttpServer buildChain(Consumer<Chain> buildFn) {
        buildFn.accept(chain)
        this
    }


    /**
     * 错误处理
     * @param ex
     * @param ctx
     */
    void errHandle(Exception ex, HttpContext ctx) {
        log.error("Request Error '" + ctx.request.id + "', url: " + ctx.request.rowUrl, ex)
        ctx.render ApiResp.of(ctx.respCode?:'01', (ex.class.simpleName + (ex.message ? ": $ex.message" : '')))
    }


    /**
     * 接收新连接
     */
    protected void accept() {
        ssc.accept(this, handler)
    }


    @EL(name = ['http.hp', 'web.hp'], async = false)
    String getHp() {
        String ip = hpCfg.split(":")[0]
        if (!ip || ip == 'localhost') {ip = ipv4()}
        ip + ':' + port
    }


    /**
     * 统计每小时的处理 http 数据包个数
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
            if (c != null) log.info("{} 时共处理 http 请求: {} 个", lastHour, c)
        }
    }


    protected class AcceptHandler implements CompletionHandler<AsynchronousSocketChannel, HttpServer> {

        @Override
        void completed(final AsynchronousSocketChannel sc, final HttpServer srv) {
            async {
                def rAddr = ((InetSocketAddress) sc.remoteAddress)
                srv.log.debug("New HTTP(AIO) Connection from: " + rAddr.hostString + ":" + rAddr.port)
                sc.setOption(StandardSocketOptions.SO_REUSEADDR, true)
                sc.setOption(StandardSocketOptions.SO_RCVBUF, 64 * 1024)
                sc.setOption(StandardSocketOptions.SO_SNDBUF, 64 * 1024)
                sc.setOption(StandardSocketOptions.SO_KEEPALIVE, true)
                def se = new HttpAioSession(sc, srv)
                se.start()
                handleExpire(se)
            }
            // 继续接入
            srv.accept()
        }

        protected void handleExpire(HttpAioSession se) {
            long expire = Duration.ofMinutes(getInteger("aioSession.maxIdle", 3)).toMillis()
            final AtomicBoolean end = new AtomicBoolean(false)
            long cur = System.currentTimeMillis()
            sched?.dyn({
                if (System.currentTimeMillis() - (se.lastUsed?:cur) > expire && end.compareAndSet(false, true)) {
                    log.debug("Closing expired HttpAioSession: " + se)
                    se.close()
                }
            }, {
                if (end.get()) return null
                long left = expire - (System.currentTimeMillis() - (se.lastUsed?:cur))
                if (left < 1000L) return new Date(System.currentTimeMillis() + (1000L * 30)) // 执行函数之前会计算下次执行的时间
                def d = new Date(System.currentTimeMillis() + (left?:0) + 10L)
                d
            })
        }

        @Override
        void failed(Throwable ex, HttpServer srv) {
            srv.log.error(ex.message?:ex.class.simpleName, ex)
        }
    }
}

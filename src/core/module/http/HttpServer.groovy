package core.module.http

import cn.xnatural.enet.event.EL
import core.Utils
import core.module.ServerTpl
import core.module.http.mvc.Chain
import core.module.http.mvc.Path

import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.text.SimpleDateFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder
import java.util.function.BiConsumer
import java.util.function.Consumer

class HttpServer extends ServerTpl {
    protected final CompletionHandler<AsynchronousSocketChannel, HttpServer> handler = new AcceptHandler()
    protected       AsynchronousServerSocketChannel                          ssc
    @Lazy           String                                                   hp      = getStr('hp', ":9090")
    @Lazy           Integer                                                  port    = hp.split(":")[1] as Integer
    protected final Chain                                                    chain   = new Chain()
    protected final List                                            ctrls   = new LinkedList<>()
    // 是否可用
    boolean                       enabled     = false


    HttpServer(String name) { super(name) }
    HttpServer() { super('web') }


    @EL(name = 'sys.starting', async = true)
    void start() {
        if (ssc) throw new RuntimeException("$name is already running")
        def cg = AsynchronousChannelGroup.withThreadPool(exec)
        ssc = AsynchronousServerSocketChannel.open(cg)
        ssc.setOption(StandardSocketOptions.SO_REUSEADDR, true)
        ssc.setOption(StandardSocketOptions.SO_RCVBUF, getInteger('so_revbuf', 64 * 1024))

        String host = hp.split(":")[0]
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
        chain.handle(new HttpContext(request, this))
        // dispatcher.dispatch()
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
        def fn = {Object ctrl, Chain ch ->
            def parantAnno = ctrl.class.getAnnotation(Path)
            Utils.iterateMethod(ctrl.class, { method ->
                def anno = method.getAnnotation(Path)
                if (!anno) return
                log.info("Add request mapping: " + ((parantAnno && parantAnno.path() ? parantAnno.path() + "/" : '') + anno.path()))

                def ps = method.getParameters()
                // 实际@Path 方法 调用
                def invoke = {HttpContext ctx ->
                    def result = method.invoke(
                        ctrl,
                        ps.collect {p ->
                            if (p instanceof HttpContext) return ctx
                            else if (p instanceof Consumer && void.class.isAssignableFrom(method.returnType)) {
                                return {r -> ctx.render(r)} as Consumer
                            }
                            ctx.param(p.name, p.type)
                        }.toArray()
                    )
                    if (!void.class.isAssignableFrom(method.returnType)) {
                        ctx.render(result)
                    }
                }

                if (anno.path()) {
                    if (anno.method()) {
                        if ('get'.equalsIgnoreCase(anno.method())) {
                            ch.get(anno.path()) {invoke(it)}
                        } else if ('post'.equalsIgnoreCase(anno.method())) {
                            ch.post(anno.path()) {invoke(it)}
                        }
                    } else {
                        ch.path(anno.path()) {invoke(it)}
                    }
                } else {
                    ch.all{invoke(it)}
                }
            })
        }
        ctrls?.each {ctrl ->
            ep.addListenerSource(ctrl)
            def anno = ctrl.class.getAnnotation(Path)
            if (anno) {
                chain.prefix(anno.path(), {ch -> fn(ctrl, ch)})
            } else {
                fn(ctrl, chain)
            }
        }
    }


    HttpServer buildChain(Consumer<Chain> buildFn) {
        buildFn.accept(chain)
        this
    }


    /**
     * 错误处理
     * @param errorHandler
     * @return
     */
    HttpServer errorHandle(BiConsumer<Exception, HttpContext> errorHandler) {
        chain.errorHandler = errorHandler
        this
    }


    /**
     * 接收新连接
     */
    protected void accept() {
        ssc.accept(this, handler)
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
                new HttpAioSession(sc, srv).start()
            }
            // 继续接入
            srv.accept()
        }

        @Override
        void failed(Throwable ex, HttpServer srv) {
            srv.log.error(ex.message?:ex.class.simpleName, ex)
        }
    }
}

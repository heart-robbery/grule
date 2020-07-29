package core.module.http

import cn.xnatural.enet.event.EL
import core.Utils
import core.module.ServerTpl
import core.module.http.mvc.Chain
import core.module.http.mvc.Path
import ctrl.CtrlTpl
import ctrl.common.ApiResp

import java.lang.reflect.Method
import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.text.SimpleDateFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder
import java.util.function.Consumer

class HttpServer extends ServerTpl {
    protected final CompletionHandler<AsynchronousSocketChannel, HttpServer> handler = new AcceptHandler()
    protected       AsynchronousServerSocketChannel                          ssc
    @Lazy           String                                                   hp      = getStr('hp', ":9090")
    @Lazy           Integer                                                  port    = hp.split(":")[1] as Integer
    protected final Chain                                                    chain   = new Chain()
    protected final List<CtrlTpl>                                            ctrls   = new LinkedList<>()
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
        log.info("Start listen HTTP(AIO) {}", port)
        initChain()
        accept()
    }


    @EL(name = 'sys.stopping', async = true)
    void stop() { ssc?.close() }


    @EL(name = 'sys.started', async = true)
    protected void started() { ctrls.each {ep.fire('inject', it)}; enabled = true }


    /**
     * 接收新的 http 请求
     * @param request
     * @param session
     */
    protected void receive(HttpRequest request, HttpAioSession session) {
        log.info("Start Request '{}': {}. from: " + session.sc.remoteAddress.toString(), request.id, request.rowUrl)
        count()
        chain.handle(new HttpContext(request, session))
        // dispatcher.dispatch()
    }


    HttpServer ctrls(Class<CtrlTpl>...clzs) {
        if (!clzs) return this
        clzs.each {clz -> ctrls.add(clz.newInstance())}
        this
    }


    protected void initChain() {
        def fn = {Object ctrl, Chain ch ->
            Utils.iterateMethod(ctrl.class, { method ->
                def anno = method.getAnnotation(Path)
                if (!anno) return

                // 实际@Path 方法 调用
                if (anno.path() == null) {
                    ch.all{invoke(ctrl, it, method)}
                } else {
                    if (anno.method() == null) {
                        ch.path(anno.path()) {invoke(ctrl, it, method)}
                    } else {
                        if ('get'.equalsIgnoreCase(anno.method())) {
                            ch.get(anno.path()) {invoke(ctrl, it, method)}
                        } else if ('post'.equalsIgnoreCase(anno.method())) {
                            ch.post(anno.path()) {invoke(ctrl, it, method)}
                        }
                    }
                }
            })
        }
        ctrls?.each {ctrl ->
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
     * 接收新连接
     */
    protected void accept() {
        ssc.accept(this, handler)
    }


    protected void invoke(Object ctrl, HttpContext ctx, Method method) {
        def result
        try {
            result = method.invoke(
                ctrl,
                method.getParameters().collect {p ->
                    ctx.param(p.name, p.type)
                }.toArray()
            )

        } catch (ex) {
            result = ApiResp.fail(ex.message)
        }
        if (!void.class.isAssignableFrom(method.returnType)) {
            ctx.render(result)
        }
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
                def se = new HttpAioSession(sc, srv.exec)
                se.handler = {req -> receive(req, se)}
                se.start()
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

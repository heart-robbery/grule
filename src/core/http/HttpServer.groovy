package core.http

import cn.xnatural.enet.event.EL
import core.SchedSrv
import core.ServerTpl
import core.Utils
import core.http.mvc.*
import core.http.ws.WS
import core.http.ws.WebSocket
import sun.security.x509.*

import java.lang.reflect.InvocationTargetException
import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.security.AccessControlException
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.LongAdder
import java.util.function.Consumer

import static core.Utils.ipv4

/**
 * 基于 AIO 的 Http 服务
 */
class HttpServer extends ServerTpl {
    protected final CompletionHandler<AsynchronousSocketChannel, HttpServer> handler = new AcceptHandler()
    protected       AsynchronousServerSocketChannel ssc
    @Lazy protected  String                hpCfg        = getStr('hp', ":8080")
    @Lazy           Integer                port         = hpCfg.split(":")[1] as Integer
    protected final Chain                  chain        = new Chain(this)
    protected final List                   ctrls        = new LinkedList<>()
    // 是否可用
    boolean                                enabled      = false
    // 当前连接数
    protected  final Queue<HttpAioSession> connections  = new ConcurrentLinkedQueue<>()
    @Lazy protected Set<String>            ignoreSuffix = new HashSet(['.js', '.css', '.html', '.vue', '.png', '.ttf', '.woff', '.woff2', 'favicon.ico', '.map', *attrs()['ignorePrintUrlSuffix']?:[]])

    HttpServer(String name) { super(name) }
    HttpServer() { super('web') }


    @EL(name = 'sys.starting', async = true)
    void start() {
        if (ssc) throw new RuntimeException("$name is already running")
        def cg = AsynchronousChannelGroup.withThreadPool(exec)
        ssc = AsynchronousServerSocketChannel.open(cg)
        ssc.setOption(StandardSocketOptions.SO_REUSEADDR, true)
        ssc.setOption(StandardSocketOptions.SO_RCVBUF, getInteger('so_revbuf', 1024 * 1024 * 4))

        String host = hpCfg.split(":")[0]
        def addr = new InetSocketAddress(port)
        if (host) {addr = new InetSocketAddress(host, port)}

        ssc.bind(addr, getInteger('backlog', 128))
        initChain()
        accept()
        log.info("Start listen HTTP(AIO) {}", hpCfg)
    }


    @EL(name = 'sys.stopping', async = true)
    void stop() { ssc?.close() }


    @EL(name = 'sys.started', async = true)
    protected void started() {
        enabled = true
        bean(SchedSrv)?.cron(getStr("0 */3 * * * ?", "cleanCron")) {clean()}
    }


    /**
     * 接收新的 http 请求
     * @param request
     */
    protected void receive(HttpRequest request) {
        // 打印请求
        if (!ignoreSuffix.find{request.path.endsWith(it)}) {
            log.info("Start Request '{}': {}. from: " + request.session.sc.remoteAddress.toString(), request.id, request.rowUrl)
        }
        count()
        HttpContext hCtx
        try {
            hCtx = new HttpContext(request, this)
            if (enabled) {
                if (app.sysLoad == 10 || connections.size() > getInteger("maxConnections", 128)) { // 限流
                    hCtx.response.status(503)
                    hCtx.render(ApiResp.of('503', "服务忙, 请稍后再试!"))
                    return
                }
                chain.handle(hCtx)
            } else {
                hCtx.response.status(503)
                hCtx.render(ApiResp.fail('请稍候...'))
            }
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
            exposeBean(ctrl)
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
                def ps = method.getParameters(); method.setAccessible(true)
                aPath.path().each {path ->
                    if (!path) {
                        log.error("@Path path must not be empty. {}#{}", ctrl.class.simpleName, method.name)
                        return
                    }
                    log.info("Request mapping: /" + ((aCtrl.prefix() ? aCtrl.prefix() + "/" : '') + (path == '/' ? '' : path)))
                    chain.method(aPath.method(), path, aPath.consumer()) {HttpContext hCtx -> // 实际@Path 方法 调用
                        try {
                            def result = method.invoke(ctrl, ps.collect {p -> hCtx.param(p.name, p.type)}.toArray())
                            if (!void.class.isAssignableFrom(method.returnType)) {
                                log.debug("Invoke Handler '" + (ctrl.class.simpleName + '#' + method.name) + "', result: " + result)
                                hCtx.render(result)
                            }
                        } catch (InvocationTargetException ex) {
                            throw ex.cause
                        }
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
                log.info("Request filter: /" + (aCtrl.prefix()) + ". {}#{}", ctrl.class.simpleName, method.name)
                def ps = method.getParameters(); method.setAccessible(true)
                chain.filter({HttpContext ctx -> // 实际@Filter 方法 调用
                    method.invoke(ctrl, ps.collect {p -> ctx.param(p.name, p.type)}.toArray())
                }, aFilter.order())
                return
            }
            def aWS = method.getAnnotation(WS)
            if (aWS) { // WS(websocket) 处理
                if (!void.class.isAssignableFrom(method.returnType)) {
                    log.error("@WS return type must be void. {}#{}", ctrl.class.simpleName, method.name)
                    return
                }
                if (!(method.parameterCount == 1 && WebSocket == method.parameters[0].type)) {
                    log.error("@WS parameter must be WebSocket. {}#{}", ctrl.class.simpleName, method.name)
                    return
                }
                log.info("WebSocket: /" + ((aCtrl.prefix() ? aCtrl.prefix() + "/" : '') + aWS.path()))
                chain.ws(aWS.path()) {ctx ->
                    try {
                        // 响应握手
                        ctx.response.status(101)
                        ctx.response.header('Upgrade', 'websocket')
                        ctx.response.header('Connection', 'Upgrade')

                        def bs1 = ctx.request.getHeader('Sec-WebSocket-Key').getBytes('utf-8')
                        def bs2 = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11'.getBytes('utf-8')
                        byte[] bs = new byte[bs1.length + bs2.length]
                        System.arraycopy(bs1, 0, bs, 0, bs1.length)
                        System.arraycopy(bs2, 0, bs, bs1.length, bs2.length)
                        ctx.response.header('Sec-WebSocket-Accept', Base64.encoder.encodeToString(Utils.sha1(bs)))
                        ctx.response.header('Sec-WebSocket-Location', 'ws://' + ep.fire('web.hp') + '/' + aCtrl.prefix() + '/' + aWS.path())
                        ctx.render(null)

                        method.invoke(ctrl, ctx.aioSession.ws)
                    } catch (InvocationTargetException ex) {
                        log.error("", ex.cause)
                        ctx.close()
                    }
                }
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
        if (ex instanceof AccessControlException) {
            log.error("Request Error '" + ctx.request.id + "', url: " + ctx.request.rowUrl + ", " + ex.message)
            ctx.render ApiResp.of(ctx.respCode?:'403', (ex.message ? ": $ex.message" : ''))
            return
        }
        log.error("Request Error '" + ctx.request.id + "', url: " + ctx.request.rowUrl, ex)
        ctx.render ApiResp.of(ctx.respCode?:'01', ctx.respMsg?:(ex.class.simpleName + (ex.message ? ": $ex.message" : '')))
    }


    /**
     * 接收新连接
     */
    protected void accept() { ssc.accept(this, handler) }


    @EL(name = ['http.hp', 'web.hp'], async = false)
    String getHp() {
        String ip = hpCfg.split(":")[0]
        if (!ip || ip == 'localhost') {ip = ipv4()}
        ip + ':' + port
    }



    /**
     * https
     * @return
     */
    protected Tuple2<PrivateKey, X509Certificate> security() {
        SecureRandom random = new SecureRandom()
        def gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(2048, random)
        def pair = gen.genKeyPair()

        X509CertInfo info = new X509CertInfo()
        X500Name owner = new X500Name("C=x,ST=x,L=x,O=x,OU=x,CN=x")
        info.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3))
        info.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(new BigInteger(64, random)))
        try {
            info.set(X509CertInfo.SUBJECT, new CertificateSubjectName(owner))
        } catch (CertificateException ignore) {
            info.set(X509CertInfo.SUBJECT, owner)
        }
        try {
            info.set(X509CertInfo.ISSUER, new CertificateIssuerName(owner));
        } catch (CertificateException ignore) {
            info.set(X509CertInfo.ISSUER, owner);
        }
        info.set(X509CertInfo.VALIDITY, new CertificateValidity(
            new Date(System.currentTimeMillis() - 86400000L * 365),
            new Date(253402300799000L))
        )
        info.set(X509CertInfo.KEY, new CertificateX509Key(pair.getPublic()));
        info.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(new AlgorithmId(AlgorithmId.sha256WithRSAEncryption_oid)));

        // Sign the cert to identify the algorithm that's used.
        X509CertImpl cert = new X509CertImpl(info)
        cert.sign(pair.private, "SHA256withRSA");

        // Update the algorithm and sign again.
        info.set(CertificateAlgorithmId.NAME + '.' + CertificateAlgorithmId.ALGORITHM, cert.get(X509CertImpl.SIG_ALG))
        cert = new X509CertImpl(info);
        cert.sign(pair.private, "SHA256withRSA")
        cert.verify(pair.public)

        return Tuple.tuple(pair.private, cert)
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


    /**
     * 清除已关闭或已过期的连接
     */
    protected void clean() {
        if (connections.isEmpty()) return
        long expire = Duration.ofMinutes(getInteger("aioSession.maxIdle",
            connections.size() > 100 ? 3 : (connections.size() > 50 ? 5 : (connections.size() > 30 ? 10 : 20))
        )).toMillis()

        for (def itt = connections.iterator().iterator(); itt.hasNext(); ) {
            def se = itt.next()
            if (se == null) break
            if (!se.sc.isOpen()) {
                connections.remove(se)
                se.close()
                log.debug("Cleaned unavailable HttpAioSession: " + se + ", connected: " + connections.size())
            } else if (!se.ws && System.currentTimeMillis() - se.lastUsed > expire) {
                connections.remove(se)
                se.close()
                log.debug("Closed expired HttpAioSession: " + se + ", connected: " + connections.size())
                break
            }
        }
    }


    protected class AcceptHandler implements CompletionHandler<AsynchronousSocketChannel, HttpServer> {

        @Override
        void completed(final AsynchronousSocketChannel sc, final HttpServer srv) {
            async {
                def rAddr = ((InetSocketAddress) sc.remoteAddress)
                srv.log.debug("New HTTP(AIO) Connection from: " + rAddr.hostString + ":" + rAddr.port + ", connected: " + connections.size())
                sc.setOption(StandardSocketOptions.SO_REUSEADDR, true)
                sc.setOption(StandardSocketOptions.SO_RCVBUF, getInteger('so_rcvbuf', 1024 * 1024 * 2))
                sc.setOption(StandardSocketOptions.SO_SNDBUF, getInteger('so_sndbuf', 1024 * 1024 * 4)) // 必须大于 chunk 最小值
                sc.setOption(StandardSocketOptions.SO_KEEPALIVE, true)
                sc.setOption(StandardSocketOptions.TCP_NODELAY, true)
                def se = new HttpAioSession(sc, srv); connections.offer(se)
                se.closeFn = {connections.remove(se)}
                se.start()
                if (connections.size() > 80) clean()
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

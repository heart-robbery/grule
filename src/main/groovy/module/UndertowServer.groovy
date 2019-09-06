//package module
//
//import cn.xnatural.enet.common.Devourer
//import cn.xnatural.enet.core.AppContext
//import cn.xnatural.enet.event.EL
//import cn.xnatural.enet.server.ServerTpl
//import io.undertow.Handlers
//import io.undertow.Undertow
//import io.undertow.server.HttpHandler
//import io.undertow.server.HttpServerExchange
//import io.undertow.server.handlers.PathHandler
//import io.undertow.server.session.*
//import io.undertow.util.Headers
//import io.undertow.util.Methods
//import org.apache.commons.lang3.time.DateUtils
//import rest.ApiResp
//
//import javax.annotation.Resource
//import java.text.SimpleDateFormat
//import java.util.concurrent.ConcurrentHashMap
//import java.util.concurrent.Executor
//import java.util.concurrent.atomic.LongAdder
//
//import static cn.xnatural.enet.common.Utils.*
//
//class UndertowServer extends ServerTpl {
//
//    Undertow   undertow
//    protected @Resource Executor exec
//    /**
//     * 吞噬器.请求执行控制器
//     */
//    protected          Devourer   devourer
//
//    // 接口 root path handler
//    protected PathHandler pathHandler = new PathHandler(10) {
//        @Override
//        PathHandler addPrefixPath(String path, HttpHandler handler) {
//            log.debug("WEB prefix path ${path}")
//            return super.addPrefixPath(path, handler)
//        }
//
//        @Override
//        PathHandler addExactPath(String path, HttpHandler handler) {
//            log.debug("WEB exact path ${path}")
//            return super.addExactPath(path, handler)
//        }
//    };
//
//
//    UndertowServer() {
//        super('undertow')
//        attr("port", 8080)
//        attr("host", 'localhost')
//    }
//
//
//    @EL(name = "sys.starting")
//    def start() {
//        attrs.putAll((Map) ep.fire("env.ns", "web", getName()))
//        undertow = Undertow.builder()
//                .addHttpListener(getInteger('port', null), getStr('host', null))
//                .setIoThreads(1).setWorkerThreads(1)
//                .setHandler(rootHandler())
//                // .setHandler(memSession(exHandler()))
//                .build()
//        undertow.start()
//        exposeBean(pathHandler)
//        // 初始化请求执行控制器
//        devourer = new Devourer(getClass().getSimpleName(), exec);
//        devourer.pause({
//            // 判断线程池里面等待执行的任务是否过多(避免让线程池里面全是请求任务在执行)
//            if (toInteger(invoke(findMethod(exec.getClass(), "getWaitingCount"), exec), 0) >
//                Math.min(Runtime.getRuntime().availableProcessors(), toInteger(invoke(findMethod(exec.getClass(), "getCorePoolSize"), exec), 4) * 2)
//            ) return true;
//            return false;
//        })
//        log.info "Start listen HTTP ${undertow.listenerInfo[0].address}"
//        ep.fire("${name}.started"); ep.fire('web.started')
//
//        // http 关闭系统
//        pathHandler
//            .addExactPath("/shutdown", Handlers.ipAccessControl({eg -> bean(AppContext.class).stop()}, false).addAllow("127.0.0.1"))
//    }
//
//
//    @EL(name = "sys.stopping")
//    def stop() {
//        undertow?.stop()
//    }
//
//
//    @EL(name = 'http.getHp', async = false)
//    def httpHp() {
//        if (undertow) return getStr('host', null) + ':' + getInteger('port', null)
//        return null
//    }
//
//
//    protected def memSession(HttpHandler next) {
//        def sm = new InMemorySessionManager('mem-session');
//        sm.registerSessionListener(new SessionListener() {
//            @Override
//            void sessionCreated(Session se, HttpServerExchange seg) {
//                log.info "HTTP session created. id: ${se.id}"
//            }
//
//            @Override
//            void sessionDestroyed(Session se, HttpServerExchange seg, SessionListener.SessionDestroyedReason reason) {
//                log.info "HTTP session destroyed. id: ${se.id}"
//            }
//        })
//        new SessionAttachmentHandler(
//                {eg ->
//                    SessionConfig sc = eg.getAttachment(SessionConfig.ATTACHMENT_KEY);
//
//                    def se = sm.getSession(eg, sc)
//                    if (se == null) se = sm.createSession(eg, sc)
//
//                    if (next) next.handleRequest(eg)
//                },
//                sm,
//                new SessionCookieConfig()
//        )
//    }
//
//
//    protected def rootHandler() {
//        new HttpHandler() {
//            @Override
//            void handleRequest(HttpServerExchange eg) throws Exception {
//                int i = devourer.getWaitingCount();
//                // 当请求对列中等待处理的请求过多就拒绝新的请求(默认值: 线程池的线程个数的3倍)
//                if (i >= getInteger("maxWaitRequest", toInteger(invoke(findMethod(exec.getClass(), "getCorePoolSize"), exec), 10) * 3)) {
//                    if (i > 0 && i % 3 == 0) log.warn("There are currently {} requests waiting to be processed.", i)
//                    eg.setStatusCode(503)
//                    eg.endExchange()
//                } else {
//                    eg.dispatch(exec, {devourer.offer({exec.execute({count(); process(eg)})})})
//                }
//            }
//        }
//    }
//
//
//    // 请求处理
//    protected void process(HttpServerExchange eg) {
//        try {
//            // def eg = hse as GroovyObject
//            println('eg is GroovyObject: ' + (eg instanceof GroovyObject))
//            println('eg metaClass: ' + eg.metaClass)
//            // 统一打印参数
//            if (!eg.requestPath.contains('favicon.ico')) {
//                if (Methods.GET.equals(eg.requestMethod)) {
//                    log.info("REQUEST STARTING. get ${eg.requestPath}, queryString: ${eg.queryString}")
//                } else if (Methods.POST.equals(eg.requestMethod)){
//                    if (eg.getRequestHeaders().getFirst(Headers.CONTENT_TYPE).contains('application/json')) {
//                        eg.startBlocking()
//                        def s = eg.inputStream.getText('utf-8');
//                        eg.properties.put('jsonStr', s)
//                        log.info("REQUEST STARTING. path: ${eg.requestPath}, jsonParamStr: ${s}")
////                        eg.responseSender.send(ApiResp.ok(eg.properties['jsonStr']).toJSONStr())
////                        return
//                    } else if (eg.getRequestHeaders().getFirst(Headers.CONTENT_TYPE).contains('application/x-www-form-urlencoded')) {
//                        eg.startBlocking()
//                        def s = eg.inputStream.getText('utf-8');
//                        eg['paramStr'] = s
//                        log.info("REQUEST STARTING. path: ${eg.requestPath}, queryString: ${eg.queryString}, paramStr: ${s}")
//                    }
//                }
//            }
//            // memSession(pathHandler).handleRequest(eg)
//            pathHandler.handleRequest(eg)
//        } catch (Throwable ex) { // 接口统一错误处理
//            def resp = ApiResp.fail(ex.getMessage() ? ex.getClass().simpleName : ex.getMessage());
//            log.error(ex, "REQUEST ERROR!, ${eg.requestPath}, reqId '${resp.reqId}'")
//            eg.responseSender.send(resp.toJSONStr())
//        }
//    }
//
//
//    @EL(name = "undertow.addPrefixPath")
//    def addPrefixPath(String path, HttpHandler h) {
//        pathHandler.addPrefixPath(path, h)
//    }
//
//
//    @EL(name = "undertow.addExactPath")
//    def addExactPath(String path, HttpHandler h) {
//        pathHandler.addExactPath(path, h)
//    }
//
//
//    /**
//     * 统计每小时的处理 http 请求个数
//     * MM-dd HH -> 个数
//     */
//    protected Map<String, LongAdder> hourCount = new ConcurrentHashMap<>(3);
//    protected void count() {
//        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH");
//        boolean isNew = false;
//        String hStr = sdf.format(new Date());
//        LongAdder count = hourCount.get(hStr);
//        if (count == null) {
//            synchronized (hourCount) {
//                count = hourCount.get(hStr);
//                if (count == null) {
//                    count = new LongAdder(); hourCount.put(hStr, count);
//                    isNew = true;
//                }
//            }
//        }
//        count.increment();
//        if (isNew) {
//            String lastHour = sdf.format(DateUtils.addHours(new Date(), -1));
//            LongAdder c = hourCount.remove(lastHour);
//            if (c != null) log.info("{} 时共处理 http 请求: {} 个", lastHour, c);
//        }
//    }
//
//
//}

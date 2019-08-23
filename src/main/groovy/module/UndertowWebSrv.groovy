package module

import cn.xnatural.enet.common.Utils
import cn.xnatural.enet.core.AppContext
import cn.xnatural.enet.event.EL
import cn.xnatural.enet.event.EP
import cn.xnatural.enet.server.ServerTpl
import groovy.transform.Field
import io.undertow.Handlers
import io.undertow.Undertow
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.PathHandler
import io.undertow.server.handlers.builder.PredicatedHandlersParser
import io.undertow.server.handlers.form.FormDataParser
import io.undertow.server.handlers.form.MultiPartParserDefinition
import io.undertow.server.handlers.resource.ClassPathResourceManager
import io.undertow.server.handlers.resource.FileResourceManager
import io.undertow.server.session.InMemorySessionManager
import io.undertow.server.session.Session
import io.undertow.server.session.SessionAttachmentHandler
import io.undertow.server.session.SessionConfig
import io.undertow.server.session.SessionCookieConfig
import io.undertow.server.session.SessionListener
import io.undertow.util.Headers
import io.undertow.websockets.core.AbstractReceiveListener
import io.undertow.websockets.core.BufferedTextMessage
import io.undertow.websockets.core.WebSocketChannel
import io.undertow.websockets.core.WebSockets
import rest.ApiResp
import rest.FileData
import sevice.FileUploader

import javax.annotation.Resource

class UndertowWebSrv extends ServerTpl {
    Undertow undertow
    @Resource AppContext ctx
    protected PathHandler pathHandler = new PathHandler();

    UndertowWebSrv() {
        super("web")
        attr("port", 8080)
        attr("host", 'localhost')
    }


    @EL(name = "sys.starting")
    def start() {
        attrs.putAll((Map) ep.fire("env.ns", getName()))
        undertow = Undertow.builder()
                .addHttpListener(getInteger('port', null), getStr('host', null))
                .setIoThreads(1).setWorkerThreads(1)
                .setHandler(pathHandler)
                // .setHandler(memSession(pathHandler))
                .build()
        undertow.start()
        exposeBean(pathHandler)
        log.info "Start listen HTTP ${undertow.listenerInfo[0].address}"
        ep.fire("${name}.started")
    }


    @EL(name = "sys.stopping")
    def stop() {
        undertow?.stop()
    }


    @EL(name = "http.getHp")
    def httpHp() {
        if (undertow) return getStr('host', null) + ":" + getInteger('port', null)
        return null
    }


    def memSession(HttpHandler next) {
        def sm = new InMemorySessionManager('mem-session');
        sm.registerSessionListener(new SessionListener() {
            @Override
            void sessionCreated(Session se, HttpServerExchange seg) {
                log.info "HTTP session created. id: ${se.id}"
            }

            @Override
            void sessionDestroyed(Session se, HttpServerExchange seg, SessionListener.SessionDestroyedReason reason) {
                log.info "HTTP session destroyed. id: ${se.id}"
            }
        })
        new SessionAttachmentHandler(
                {eg ->
                    SessionConfig sc = eg.getAttachment(SessionConfig.ATTACHMENT_KEY);

                    def se = sm.getSession(eg, sc)
                    if (se == null) se = sm.createSession(eg, sc)

                    if (next) next.handleRequest(eg)
                },
                sm,
                new SessionCookieConfig()
        )
    }


    // 接口控制层
    def ctrl() {



        def preH = { String el, HttpHandler next ->
            Handlers.predicates(PredicatedHandlersParser.parse(el, getClass().getClassLoader()), next)
        }
        FileUploader fu = ep.fire("bean.get", FileUploader.class)

        pathHandler
            .addPrefixPath("/", preH("allowed-methods(GET)", Handlers.resource(new ClassPathResourceManager(getClass().classLoader, "static"))))
            .addExactPath("/test", {eg -> eg.responseSender.send("xxxxxxxxxxxxx")})
            .addExactPath("/xxx", {eg -> eg.responseSender.send("22222222222222")})
            .addExactPath("/upload", preH("allowed-methods(POST)", { HttpServerExchange eg ->
                if (!eg.getRequestHeaders().getFirst(Headers.CONTENT_TYPE).contains('multipart/form-data')) {
                    eg.responseSender.send(ApiResp.fail("content-type must be 'multipart/form-data'").toJSONStr())
                    return
                }
                def parser = new MultiPartParserDefinition().setDefaultEncoding("utf-8").setExecutor(exec)
                parser.setFileSizeThreshold(10485760L)
                parser.create(eg).parse({hsg ->
                    def fd = hsg.getAttachment(FormDataParser.FORM_DATA)
                    fd.iterator().each {n ->
                        fd.get(n).each {fv ->
                            if (fv.isFileItem()) {
                                def fileData = new FileData();
                                fileData.inputStream = fv.fileItem.inputStream
                                fileData.size = fv.fileItem.fileSize
                                fileData.originName = fv.fileItem.file.fileName
                                fileData.generatedName = UUID.randomUUID().toString().replace("-", "")
                                fu.save(fileData)
                            }
                        }
                    }
                    hsg.responseSender.send(ApiResp.ok().toJSONStr())
                });
        }))
        .addPrefixPath("/file", Handlers.resource(new FileResourceManager(new File(fu.getLocalDir()))))
        .addExactPath("/shutdown", Handlers.ipAccessControl({eg -> ctx.stop()}, false).addAllow("127.0.0.1"))
    }


    @EL(name = "undertow.addPrefixPath")
    def addPrefixPath(String path, HttpHandler h) {
        pathHandler.addPrefixPath(path, h)
    }


    @EL(name = "undertow.addExactPath")
    def addExactPath(String path, HttpHandler h) {
        pathHandler.addExactPath(path, h)
    }

}

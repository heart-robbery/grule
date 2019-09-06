//package rest
//
//import cn.xnatural.enet.event.EL
//import cn.xnatural.enet.server.ServerTpl
//import io.undertow.Handlers
//import io.undertow.server.HttpHandler
//import io.undertow.server.HttpServerExchange
//import io.undertow.server.handlers.PathHandler
//import io.undertow.server.handlers.builder.PredicatedHandlersParser
//import io.undertow.server.handlers.form.FormDataParser
//import io.undertow.server.handlers.form.MultiPartParserDefinition
//import io.undertow.server.handlers.resource.ClassPathResourceManager
//import io.undertow.server.handlers.resource.FileResourceManager
//import io.undertow.util.Headers
//import io.undertow.websockets.core.AbstractReceiveListener
//import io.undertow.websockets.core.BufferedTextMessage
//import io.undertow.websockets.core.WebSocketChannel
//import io.undertow.websockets.core.WebSockets
//import sevice.FileUploader
//
//import java.util.concurrent.Executor
//
//class TplRest extends ServerTpl {
//
//    def preH = { String el, HttpHandler next ->
//        Handlers.predicates(PredicatedHandlersParser.parse(el, getClass().getClassLoader()), next)
//    }
//
//    // content-type 验证
//    def cType = {eg, String type ->
//        if (eg.getRequestHeaders().getFirst(Headers.CONTENT_TYPE).contains(type)) {return true}
//        eg.responseSender.send(ApiResp.fail("content-type must be '${type}'").toJSONStr())
//        false
//    }
//
//
//    @EL(name = "undertow.started")
//    protected void init() {
//        def h = bean(PathHandler.class);
//        h.addPrefixPath("/", index())
//        h.addExactPath("/ws", ws())
//        h.addExactPath("/test", {eg -> eg.responseSender.send(ApiResp.ok('test').toJSONStr())})
//        h.addExactPath('/upload', upload())
//        h.addPrefixPath('/file', file())
//        h.addPrefixPath('/json', appJson())
//    }
//
//
//    // 主页, 及静态文件
//    def index() {
//        preH("allowed-methods(GET)", Handlers.resource(new ClassPathResourceManager(getClass().classLoader, "static")))
//    }
//
//
//    // webSocket
//    def ws() {
//        // webSocket 处理器
//        def ws = Handlers.websocket({ eg, ch ->
//            log.info "New WebSocket Connection '${ch.peerAddress}', current total: ${eg.peerConnections.size()}"
//            ch.getReceiveSetter().set(new AbstractReceiveListener() {
//                @Override
//                protected void onFullTextMessage(WebSocketChannel wsc, BufferedTextMessage msg) throws IOException {
//                    log.info "receive websocket client '${wsc.peerAddress}' msg: ${msg.data}"
//                }
//            })
//            ch.resumeReceives()
//        })
//        ep.fire("sched.cron", "0/17 * * * * ?", {ws.peerConnections.each {conn -> WebSockets.sendText("qqqq" + new Date(), conn, null)}})
//        return ws
//    }
//
//
//    // 上传文件
//    def upload() {
//        def fu = bean(FileUploader.class)
//        def exec = bean(Executor.class)
//        preH("allowed-methods(POST)", { HttpServerExchange eg ->
//            if (!cType(eg, 'multipart/form-data')) return
//
//            def parser = new MultiPartParserDefinition().setDefaultEncoding('utf-8').setExecutor(exec)
//            parser.setFileSizeThreshold(10485760L)
//
//            // 解析出form 表单的数据
//            eg.startBlocking()
//            def fd = parser.create(eg).parseBlocking()
//
//            fd.iterator().each {n ->
//                fd.get(n).each {fv ->
//                    if (fv.isFileItem()) {
//                        def fileData = new FileData();
//                        fileData.inputStream = fv.fileItem.inputStream
//                        fileData.size = fv.fileItem.fileSize
//                        fileData.originName = fv.fileName
//                        fu.save(fileData)
//                    }
//                }
//            }
//            eg.responseSender.send(ApiResp.ok().toJSONStr())
//        })
//    }
//
//
//    // 返回上传的文件
//    def file() {
//        // 文件路径 大小区别: io.undertow.server.handlers.resource.PathResourceManager.isFileSameCase
//        Handlers.resource(FileResourceManager.builder()
//            .setCaseSensitive(false)
//            .setBase(new File(bean(FileUploader.class).getLocalDir()).toPath())
//            .build()
//        )
//    }
//
//
//    // content-type application/json
//    def appJson() {
//        preH("allowed-methods(POST)", { HttpServerExchange eg ->
//            if (!cType(eg, 'application/json')) return
//            eg.responseSender.send(ApiResp.ok(eg.properties['jsonStr']).toJSONStr())
//        })
//    }
//}

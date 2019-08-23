package rest

import cn.xnatural.enet.event.EL
import cn.xnatural.enet.server.ServerTpl
import io.undertow.Handlers
import io.undertow.server.HttpHandler
import io.undertow.server.handlers.PathHandler
import io.undertow.server.handlers.builder.PredicatedHandlersParser
import io.undertow.server.handlers.resource.ClassPathResourceManager
import io.undertow.websockets.core.AbstractReceiveListener
import io.undertow.websockets.core.BufferedTextMessage
import io.undertow.websockets.core.WebSocketChannel
import io.undertow.websockets.core.WebSockets

class TplRest extends ServerTpl {

    def preH = { String el, HttpHandler next ->
        Handlers.predicates(PredicatedHandlersParser.parse(el, getClass().getClassLoader()), next)
    }


    @EL(name = "web.started")
    protected void init() {
        def h = bean(PathHandler.class);
        h.addPrefixPath("/", index())
        h.addExactPath("/ws", ws())
    }


    def index() {
        preH("allowed-methods(GET)", Handlers.resource(new ClassPathResourceManager(getClass().classLoader, "static")))
    }


    def ws() {
        // webSocket 处理器
        def ws = Handlers.websocket({ eg, ch ->
            log.info "New WebSocket Connection '${ch.peerAddress}', current total: ${eg.peerConnections.size()}"
            ch.getReceiveSetter().set(new AbstractReceiveListener() {
                @Override
                protected void onFullTextMessage(WebSocketChannel wsc, BufferedTextMessage msg) throws IOException {
                    log.info "receive websocket client '${wsc.peerAddress}' msg: ${msg.data}"
                }
            })
            ch.resumeReceives()
        })
        ctx.ep.fire("sched.cron", "0/17 * * * * ?", {ws.peerConnections.each {conn -> WebSockets.sendText("qqqq" + new Date(), conn, null)}})
        return ws
    }
}

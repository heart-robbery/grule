package core.module.http.ws

import core.module.http.HttpAioSession

class WebSocket {
    protected final HttpAioSession session
    protected Listener             listener
    protected final WsDecoder      decoder = new WsDecoder(this)


    WebSocket(HttpAioSession session) { this.session = session }


    void send(String msg) {
        session.send(WsEncoder.encode(msg))
    }


    void close() {
        session.close()
    }


    WebSocket listen(Listener listener) {
        this.listener = listener
    }
}

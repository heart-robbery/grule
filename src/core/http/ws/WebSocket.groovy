package core.http.ws

import core.http.HttpAioSession

/**
 * web socket 连接实例
 */
class WebSocket {
    // 关联的Http aio 会话
    protected final HttpAioSession session
    // 消息监听
    protected Listener             listener
    protected final WsDecoder      decoder = new WsDecoder(this)


    WebSocket(HttpAioSession session) { this.session = session }


    /**
     * 发送消息
     * @param msg
     */
    void send(String msg) {
        session.send(WsEncoder.encode(msg))
    }


    /**
     * 关闭 当前 websocket
     */
    void close() { session.close() }


    /**
     * 设置消息监听
     * @param listener
     * @return
     */
    WebSocket listen(Listener listener) {
        this.listener = listener
        this
    }
}

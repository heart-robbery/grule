package core.module.http

import core.module.http.ws.WebSocket
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.nio.ByteBuffer
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.concurrent.atomic.AtomicBoolean

/**
 * http 连接会话
 */
class HttpAioSession {
    protected static final Logger                    log         = LoggerFactory.getLogger(HttpAioSession)
    protected final        AsynchronousSocketChannel sc
    protected final                                  readHandler = new ReadHandler(this)
    protected final        HttpServer                server
    // close 回调函数
    protected              Runnable                  closeFn
    // 上次读写时间
    protected              Long                      lastUsed
    protected final        AtomicBoolean             closed      = new AtomicBoolean(false)
    // 每次接收消息的内存空间
    @Lazy protected               def                buf         = ByteBuffer.allocate(server.getInteger('maxMsgSize', 1024 * 20))
    // 不为空代表是WebSocket
    WebSocket ws


    HttpAioSession(AsynchronousSocketChannel sc, HttpServer server) {
        assert sc != null: "sc must not be null"
        assert server != null: "server must not be null"
        this.sc = sc
        this.server = server
    }


    /**
     * 开始数据接收处理
     */
    void start() { read() }


    /**
     * 关闭
     */
    void close() {
        if (closed.compareAndSet(false, true)) {
            try {sc?.shutdownOutput()} catch(ex) {}
            try {sc?.shutdownInput()} catch(ex) {}
            try {sc?.close()} catch(ex) {}
            closeFn?.run()
        }
    }


    /**
     * 发送消息到客户端
     * @param buf
     */
    void send(ByteBuffer buf) {
        if (buf == null) return
        lastUsed = System.currentTimeMillis()
        try {
            sc.write(buf).get()
        } catch (ex) {
            log.error(ex.class.simpleName + " " + sc.localAddress.toString() + " ->" + sc.remoteAddress.toString())
            close()
        }
    }


    /**
     * 继续处理接收数据
     */
    protected void read() {
        if (closed.get()) throw new RuntimeException("已关闭 " + this)
        sc.read(buf, buf, readHandler)
    }


    @Override
    String toString() {
        return getClass().simpleName + "@" + Integer.toHexString(hashCode()) + "[" + sc?.toString() + "]"
    }


    protected class ReadHandler implements CompletionHandler<Integer, ByteBuffer> {
        final HttpAioSession session
        // 当前解析的请求
        HttpRequest request

        ReadHandler(HttpAioSession session) { assert session != null; this.session = session }

        @Override
        void completed(Integer count, ByteBuffer buf) {
            if (count > 0) {
                lastUsed = System.currentTimeMillis()
                buf.flip()

                if (session.ws) { // 是 WebSocket的情况
                    session.ws.decoder.decode(buf)
                } else { // 正常 http 请求
                    if (request == null) {request = new HttpRequest(session)}
                    try {
                        request.decoder.decode(buf)
                    } catch (ex) {
                        log.error("HTTP 解析出错", ex)
                        close(); return
                    }
                    if (request.decoder.complete) {
                        if (request.decoder.websocket) { // 创建WebSocket 会话
                            session.ws = new WebSocket(session)
                            //server.receive(ws)
                            server.receive(request)
                        } else {
                            def req = request
                            request = null // 下一个请求
                            server.receive(req)
                        }
                    }
                }

                buf.compact()
                // 避免 ReadPendingException
                session.read()
            }
            else {
                log.warn("接收字节为空. 关闭 " + session.sc.toString())
                session.close()
            }
        }


        @Override
        void failed(Throwable ex, ByteBuffer buf) {
            if (!(ex instanceof AsynchronousCloseException)) log.error("", ex)
            session.close()
        }
    }
}

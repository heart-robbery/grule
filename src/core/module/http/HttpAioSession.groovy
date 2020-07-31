package core.module.http

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.ClosedChannelException
import java.nio.channels.CompletionHandler
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/**
 * http 连接会话
 */
class HttpAioSession {
    protected static final Logger                    log         = LoggerFactory.getLogger(HttpAioSession)
    protected final        AsynchronousSocketChannel sc
    protected final                                  readHandler = new ReadHandler(this)
    protected final                                  buf         = ByteBuffer.allocate(1024 * 10)
    protected final        ExecutorService           exec
    // close 回调函数
    protected              Runnable                  closeFn
    protected              Consumer<HttpRequest>     handler
    // 上次读写时间
    protected              Long                      lastUsed
    protected final        AtomicBoolean             closed      = new AtomicBoolean(false)


    HttpAioSession(AsynchronousSocketChannel sc, ExecutorService exec) {
        assert sc != null: "sc must not be null"
        assert exec != null: "exec must not be null"
        this.sc = sc
        this.exec = exec
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
            sc?.shutdownOutput(); sc?.shutdownInput(); sc?.close()
            closeFn?.run()
        }
    }


    /**
     * 发送消息到客户端
     * @param msg
     */
    void send(String msg) {
        if (msg == null) return
        lastUsed = System.currentTimeMillis()
        try {
            sc.write(ByteBuffer.wrap(msg.getBytes('utf-8'))).get()
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
        buf.clear()
        sc.read(buf, buf, readHandler)
    }


    protected class ReadHandler implements CompletionHandler<Integer, ByteBuffer> {
        final HttpAioSession session
        final ByteBuffer buffer = ByteBuffer.allocate(buf.capacity() * 2)

        ReadHandler(HttpAioSession session) { assert session != null; this.session = session }

        @Override
        void completed(Integer count, ByteBuffer buf) {
            if (count > 0) {
                lastUsed = System.currentTimeMillis()
                buf.flip()
                handler?.accept(HttpDecoder.decode(buf))
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
            log.error("", ex)
            session.close()
        }
    }
}

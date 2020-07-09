package core.module.aio

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.concurrent.ExecutorService
import java.util.function.Consumer
import java.util.function.Function

class AioSession {
    protected static final Logger log = LoggerFactory.getLogger(AioSession)
    protected final AsynchronousSocketChannel sc
    protected final readHandler = new ReadHandler(this)
    protected final List<Function<String, String>> handlers = new LinkedList<>()
    protected final buf = ByteBuffer.allocate(1024 * 20)
    protected final ExecutorService exec


    AioSession(AsynchronousSocketChannel sc, ExecutorService exec) {
        assert sc != null: "sc must not be null"
        assert exec != null: "exec must not be null"
        this.sc = sc
        this.exec = exec
    }


    /**
     * 添加消息处理函数
     * @param handler 函数入参是消息字符串, 函数返回会被写到客户端
     * @return
     */
    AioSession addHandler(Function<String, String> handler) {
        handlers.add(handler)
        this
    }


    /**
     * 开始数据接收处理
     */
    void start() { read() }


    void stop() {sc?.close()}


    /**
     * 发送消息到客户端
     * @param msg
     */
    void send(String msg) {
        if (msg != null) {
            sc.write(ByteBuffer.wrap(msg.getBytes('utf-8')))
        }
    }


    /**
     * 继续处理接收数据
     */
    protected void read() {
        buf.clear()
        sc.read(buf, buf, readHandler)
    }


    /**
     * 接收消息处理
     * @param msg
     * @param cb 返回消息处理回调函数
     */
    protected void receive(String msg, Consumer<String> cb) {
        handlers?.each {fn ->
            try {
                def r = fn.apply(msg)
                if (r && r instanceof String) {cb.accept(r)}
            } catch(ex) {
                log.error("数据处理错误. " + msg, ex)
            }
        }
    }


    protected class ReadHandler implements CompletionHandler<Integer, ByteBuffer> {
        final AioSession session

        ReadHandler(AioSession session) { assert session != null; this.session = session }

        @Override
        void completed(Integer count, ByteBuffer buf) {
            if (count > 0) {
                buf.flip()
                byte[] bs = new byte[buf.limit()]
                buf.get(bs)
                exec.execute {receive(new String(bs, 'utf-8'), { String reply -> session.send(reply)})}
                session.read()
            }
            else {
                log.warn("接收字节为空")
                session.read()
            }
        }


        @Override
        void failed(Throwable ex, ByteBuffer buf) {
            log.error(ex.message?:ex.class.simpleName, ex)
        }
    }
}

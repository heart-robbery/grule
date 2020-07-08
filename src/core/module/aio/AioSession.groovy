package core.module.aio

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.function.Consumer
import java.util.function.Function

class AioSession {
    protected static final Logger log = LoggerFactory.getLogger(AioSession)
    protected final AsynchronousSocketChannel sc
    protected final readHandler = new ReadHandler(this)
    private List<Function<String, String>> handlers = new LinkedList<>()
    protected def buf = ByteBuffer.allocate(1024 * 4)


    AioSession(AsynchronousSocketChannel sc) { this.sc = sc }


    /**
     * 添加消息处理函数
     * @param handler 函数入参是消息字符串, 函数返回会被写到客户端
     * @return
     */
    AioSession addHandler(Function<String, String> handler) {
        handlers.add(handler)
        this
    }


    void start() {
        sc.read(buf, buf, readHandler)
    }


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

        ReadHandler(AioSession session) { this.session = session }

        @Override
        void completed(Integer count, ByteBuffer buf) {
            buf.flip()
            receive(new String(buf.array(), 'utf-8'), { String msg -> session.send(msg)})
            sc.read(buf, buf, readHandler)
        }

        @Override
        void failed(Throwable ex, ByteBuffer buf) {
            log.error(ex.message?:ex.class.simpleName, ex)
        }
    }
}

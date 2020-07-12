package core.module.aio

import core.Devourer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.ClosedChannelException
import java.nio.channels.CompletionHandler
import java.util.concurrent.ExecutorService
import java.util.function.BiConsumer

class AioSession {
    protected static final Logger log = LoggerFactory.getLogger(AioSession)
    protected final AsynchronousSocketChannel sc
    protected final readHandler = new ReadHandler(this)
    protected final buf = ByteBuffer.allocate(1024 * 20)
    protected final ExecutorService exec
    protected final List<BiConsumer<String, AioSession>> msgFns = new LinkedList<>()
    // 消息发送队列
    protected final Devourer sendQueue
    // close 回调函数
    protected Runnable closeFn

    AioSession(AsynchronousSocketChannel sc, ExecutorService exec) {
        assert sc != null: "sc must not be null"
        assert exec != null: "exec must not be null"
        this.sc = sc
        this.exec = exec
        sendQueue = new Devourer(AioSession.simpleName + ":" + sc.toString(), exec)
    }


    /**
     * 添加消息处理函数
     * @param msgFn 入参1: 是消息字符串, 入参2: 当前Session
     * @return
     */
    AioSession msgFn(BiConsumer<String, AioSession> msgFn) {if (msgFn) this.msgFns << msgFn; this}


    /**
     * 开始数据接收处理
     */
    void start() { read() }


    void close() {sc?.close(); msgFns.clear(); closeFn?.run()}


    /**
     * 发送消息到客户端
     * @param msg
     */
    void send(String msg) {
        if (msg == null) return
        sendQueue.offer {
            try {
                sc.write(ByteBuffer.wrap(msg.getBytes('utf-8'))).get()
            } catch (ClosedChannelException ex) {
                log.error("ClosedChannelException " + sc.localAddress.toString() + " ->" + sc.remoteAddress.toString())
                close()
            }
        }
    }


    boolean busy() { sendQueue.waitingCount > 0 }


    /**
     * 接收消息处理
     * @param msg
     */
    protected void receive(String msg) {
        msgFns?.each {fn ->
            exec.execute {
                try {
                    fn.accept(msg, this)
                } catch(ex) {
                    log.error("数据处理错误. " + msg, ex)
                }
            }
        }
    }



    /**
     * 继续处理接收数据
     */
    protected void read() {
        buf.clear()
        sc.read(buf, buf, readHandler)
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
                receive(new String(bs, 'utf-8'))
                session.read()
            }
            else {
                log.warn(session.sc.remoteAddress.toString() + "接收字节为空. 关闭")
                session.close()
            }
        }


        @Override
        void failed(Throwable ex, ByteBuffer buf) {
            log.error(ex.message?:ex.class.simpleName, ex)
        }
    }
}

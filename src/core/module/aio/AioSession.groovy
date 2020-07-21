package core.module.aio

import core.Devourer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.nio.ByteBuffer
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.ClosedChannelException
import java.nio.channels.CompletionHandler
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BiConsumer

class AioSession {
    protected static final Logger log = LoggerFactory.getLogger(AioSession)
    protected final AsynchronousSocketChannel sc
    protected final readHandler = new ReadHandler(this)
    protected final buf = ByteBuffer.allocate(1024 * 10)
    protected final ExecutorService exec
    protected final List<BiConsumer<String, AioSession>> msgFns = new LinkedList<>()
    // 消息发送队列
    protected final Devourer sendQueue
    // close 回调函数
    protected Runnable closeFn
    // 数据分割符(半包和粘包) 默认换行符分割
    protected String delimiter = '\n'
    // 上次读取时间
    protected Long lastReadTime
    protected final AtomicBoolean closed = new AtomicBoolean(false)


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


    /**
     * 关闭
     */
    void close() {
        if (closed.compareAndSet(false, true)) {
            sc?.close(); msgFns.clear(); closeFn?.run()
        }
    }


    /**
     * 发送消息到客户端
     * @param msg
     */
    void send(String msg) {
        if (closed.get()) throw new RuntimeException("已关闭 " + this)
        if (msg == null) return
        sendQueue.offer { // 排对发送消息. 避免 WritePendingException
            try {
                sc.write(ByteBuffer.wrap((msg + (delimiter?:'')).getBytes('utf-8'))).get()
            } catch (ClosedChannelException | AsynchronousCloseException ex) {
                log.error(ex.class.simpleName + " " + sc.localAddress.toString() + " ->" + sc.remoteAddress.toString())
                close()
            }
        }
    }


    /**
     * 当前会话渠道是否忙
     * @return
     */
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
        if (closed.get()) return
        buf.clear()
        sc.read(buf, buf, readHandler)
    }


    @Override
    String toString() {
        return getClass().simpleName + "@" + Integer.toHexString(hashCode()) + "[" + sc?.toString() + "]"
    }


    protected class ReadHandler implements CompletionHandler<Integer, ByteBuffer> {
        final AioSession session
        @Lazy byte[]     delim  = session.delimiter ? session.delimiter.getBytes('utf-8') : null
        @Lazy ByteBuffer buffer = ByteBuffer.allocate(buf.capacity() * 2)

        ReadHandler(AioSession session) { assert session != null; this.session = session }

        @Override
        void completed(Integer count, ByteBuffer buf) {
            if (count > 0) {
                lastReadTime = System.currentTimeMillis()
                buf.flip()
                if (delim == null) { // 没有分割符的时候
                    byte[] bs = new byte[buf.limit()]
                    buf.get(bs)
                    receive(new String(bs, 'utf-8'))
                } else delimit(buf)
                // 避免 ReadPendingException
                session.read()
            }
            else {
                log.warn(session.sc.remoteAddress.toString() + "接收字节为空. 关闭")
                session.close()
            }
        }


        /**
         * 分割 半包和粘包
         * @param buf
         */
        protected void delimit(ByteBuffer buf) {
            // 存放新数据
            for (int i = 0; i < buf.limit(); i++) {buffer.put(buf.get())}

            // 分割
            buffer.flip()
            do {
                int delimIndex = getDelimIndex()
                if (delimIndex < 0) break
                int readableLength = delimIndex - buffer.position()
                byte[] bs = new byte[readableLength]
                buffer.get(bs)
                receive(new String(bs, 'utf-8'))

                // 跳过 分割符的长度
                for (int i = 0; i < delim.length; i++) {buffer.get()}
            } while (true)
            buffer.compact()
        }


        // 查找分割符所匹配下标
        protected int getDelimIndex() {
            byte[] hb = buffer.array()
            int delimIndex = -1 // 分割符所在的下标
            for (int i = buffer.position(), size = buffer.limit(); i < size; i++) {
                boolean match = true // 是否找到和 delim 相同的字节串
                for (int j = 0; j < delim.length; j++) {
                    match = match && (i + j < size) && delim[j] == hb[i + j]
                }
                if (match) {
                    delimIndex = i
                    break
                }
            }
            return delimIndex
        }


        @Override
        void failed(Throwable ex, ByteBuffer buf) {
            if (ex instanceof ClosedChannelException || ex instanceof AsynchronousCloseException) session.close()
            log.error(ex.message?:ex.class.simpleName, ex)
        }
    }
}

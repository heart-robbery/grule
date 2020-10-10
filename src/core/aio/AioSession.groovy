package core.aio

import core.Devourer
import core.ServerTpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.ClosedChannelException
import java.nio.channels.CompletionHandler
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BiConsumer

/**
 * 一条AIO tcp连接会话
 */
class AioSession {
    protected static final Logger                               log         = LoggerFactory.getLogger(AioSession)
    protected final        AsynchronousSocketChannel            sc
    protected final                                             readHandler = new ReadHandler(this)
    protected final        ServerTpl                            server
    protected final        List<BiConsumer<String, AioSession>> msgFns      = new LinkedList<>()
    // 消息发送队列
    @Lazy protected        Devourer                             sendQueue   = new Devourer(AioSession.simpleName + ":" + sc.toString(), server.exec)
    // close 回调函数
    protected              Runnable                             closeFn
    protected              Long                                 lastUsed
    // 上次读写时间
    protected final        AtomicBoolean                        closed      = new AtomicBoolean(false)
    // 数据分割符(半包和粘包) 默认换行符分割
    @Lazy protected        String                               delimiter   = server.getStr('delimiter', '\n')
    // 每次接收消息的内存空间
    @Lazy protected             def                             buf         = ByteBuffer.allocate(server.getInteger('maxMsgSize', 1024 * 1024))


    AioSession(AsynchronousSocketChannel sc, ServerTpl server) {
        assert sc != null: "sc must not be null"
        assert server != null: "server must not be null"
        this.sc = sc
        this.server = server
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
            try {sc?.shutdownOutput()} catch(ex) {}
            try {sc?.shutdownInput()} catch(ex) {}
            try {sc?.close()} catch(ex) {}
            closeFn?.run()
        }
    }


    /**
     * 当前会话渠道是否忙
     * @return
     */
    boolean busy() { sendQueue.waitingCount > 1 }


    /**
     * 发送消息到客户端
     * @param msg
     */
    void send(String msg) {
        if (closed.get() || msg == null) return
        lastUsed = System.currentTimeMillis()
        sendQueue.offer { // 排对发送消息. 避免 WritePendingException
            try {
                sc.write(ByteBuffer.wrap((msg + (delimiter?:'')).getBytes('utf-8'))).get(server.getLong("aioWriteTimeout", 8000L), TimeUnit.MILLISECONDS)
            } catch (ex) {
                if (ex !instanceof ClosedChannelException) {
                    log.error(sc.localAddress.toString() + " ->" + sc.remoteAddress.toString(), ex)
                }
                close()
            }
        }
    }


    /**
     * 接收消息处理
     * @param msg
     */
    protected void receive(String msg) {
        msgFns?.each {fn ->
            server.async({fn.accept(msg, this)}) {ex ->
                log.error("数据处理错误. " + msg, ex)
            }
        }
    }


    /**
     * 继续处理接收数据
     */
    protected void read() {
        if (closed.get()) return
        sc.read(buf, buf, readHandler)
    }


    @Override
    String toString() {
        return super.toString() + "[" + sc?.toString() + "]"
    }


    protected class ReadHandler implements CompletionHandler<Integer, ByteBuffer> {
        final AioSession session
        @Lazy byte[]     delim  = session.delimiter ? session.delimiter.getBytes('utf-8') : null

        ReadHandler(AioSession session) { assert session != null; this.session = session }

        @Override
        void completed(Integer count, ByteBuffer buf) {
            if (count > 0) {
                lastUsed = System.currentTimeMillis()
                buf.flip()
                if (delim == null) { // 没有分割符的时候
                    byte[] bs = new byte[buf.limit()]
                    buf.get(bs)
                    receive(new String(bs, 'utf-8'))
                } else delimit(buf)
                // 同一时间只有一个 read, 避免 ReadPendingException
                session.read()
            }
            else {
                log.warn("接收字节为空. 关闭 " + session.sc.toString())
                session.close()
            }
        }


        /**
         * 分割 半包和粘包
         * @param buf
         */
        protected void delimit(ByteBuffer buf) {
            do {
                int delimIndex = indexOf(buf)
                if (delimIndex < 0) break
                int readableLength = delimIndex - buf.position()
                byte[] bs = new byte[readableLength]
                buf.get(bs)
                receive(new String(bs, 'utf-8'))

                // 跳过 分割符的长度
                for (int i = 0; i < delim.length; i++) {buf.get()}
            } while (true)
            buf.compact()
        }


        // 查找分割符所匹配下标
        protected int indexOf(ByteBuffer buf) {
            byte[] hb = buf.array()
            int delimIndex = -1 // 分割符所在的下标
            for (int i = buf.position(), size = buf.limit(); i < size; i++) {
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
            if (ex !instanceof ClosedChannelException) {
                log.error("", ex)
            }
            session.close()
        }
    }
}

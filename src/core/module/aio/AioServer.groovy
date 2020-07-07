package core.module.aio

import cn.xnatural.enet.event.EL
import core.module.ServerTpl

import java.nio.ByteBuffer
import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.function.Consumer
import java.util.function.Function


/**
 * Aio 服务端
 */
class AioServer extends ServerTpl {
    protected final List<Function<String, String>> msgFns = new LinkedList<>()
    protected final CompletionHandler<AsynchronousSocketChannel, AioServer> handler = new AcceptHandler()
    protected AsynchronousServerSocketChannel                        ssc
    @Lazy Integer port = getInteger('port', 1000)


    AioServer() {super('aio')}


    @EL(name = 'sys.starting', async = true)
    void start() {
        def cg = AsynchronousChannelGroup.withThreadPool(exec)
        ssc = AsynchronousServerSocketChannel.open(cg)
        ssc.setOption(StandardSocketOptions.SO_REUSEADDR, true)
        ssc.setOption(StandardSocketOptions.SO_RCVBUF, 64 * 1024)

        def host = getStr('host')
        def addr = new InetSocketAddress(port)
        if (host) {addr = new InetSocketAddress(host, port)}

        ssc.bind(addr, getInteger('backlog', 100))
        accept()
        log.info("Start listen TCP(Aio) {}", port)
    }


    @EL(name = 'sys.stopping')
    void stop() {
        ssc?.close()
    }


    /**
     * 接收消息的处理函数
     * @param msgFn
     * @return
     */
    AioServer msgFn(Function<String, String> msgFn) {if (msgFn) this.msgFns.add(msgFn); this}


    protected void accept() {
        ssc.accept(this, handler)
    }


    /**
     * 处理消息接收
     * @param msg
     * @param bw
     * @return
     */
    protected void handleReceive(String msg, Consumer<String> bw) {
        msgFns?.each {fn ->
            def r = fn.apply(msg)
            if (r && r instanceof String) {
                bw.accept(r)
            }
        }
    }


    protected class IoSession {
        final AsynchronousSocketChannel sc
        def                             readHandler = new ReadHandler(this)
        IoSession(AsynchronousSocketChannel sc) {
            this.sc = sc
            sc.setOption(StandardSocketOptions.SO_REUSEADDR, true)
            sc.setOption(StandardSocketOptions.SO_RCVBUF, 64 * 1024)
            sc.setOption(StandardSocketOptions.SO_SNDBUF, 64 * 1024)
            sc.setOption(StandardSocketOptions.SO_KEEPALIVE, true)
        }

        protected void read() {
            def buf = ByteBuffer.allocate(1024 * 4)
            sc.read(buf, buf, readHandler)
        }

        protected void write(String msg) {
            if (msg != null) {
                sc.write(ByteBuffer.wrap(msg.getBytes('utf-8')).flip())
            }
        }
    }

    protected class ReadHandler implements CompletionHandler<Integer, ByteBuffer> {
        final IoSession session

        ReadHandler(IoSession session) { this.session = session }

        @Override
        void completed(Integer count, ByteBuffer buf) {
            buf.flip()
            handleReceive(new String(buf.array(), 'utf-8'), {String msg -> session.write(msg)})
            session.read()
        }

        @Override
        void failed(Throwable ex, ByteBuffer buf) {
            log.error(ex.message?:ex.class.simpleName, ex)
        }
    }

    protected class AcceptHandler implements CompletionHandler<AsynchronousSocketChannel, AioServer> {

        @Override
        void completed(final AsynchronousSocketChannel sc, final AioServer srv) {
            try {
                def rAddr = ((InetSocketAddress) sc.remoteAddress)
                srv.log.info("New TCP(AIO) Connection from: " + rAddr.hostString + ":" + rAddr.port)
                new IoSession(sc).read()
            } catch (ex) {
                log.error("", ex)
            } finally {
                // 继续接入(异步)
                srv.accept()
            }
        }

        @Override
        void failed(Throwable ex, AioServer srv) {
            srv.log.error(ex.message?:ex.class.simpleName, ex)
        }
    }
}

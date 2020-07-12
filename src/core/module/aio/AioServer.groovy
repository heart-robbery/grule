package core.module.aio

import cn.xnatural.enet.event.EL
import core.module.ServerTpl

import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.text.SimpleDateFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder
import java.util.function.BiConsumer

/**
 * Aio 服务端
 */
class AioServer extends ServerTpl {
    protected final List<BiConsumer<String, AioSession>> msgFns = new LinkedList<>()
    protected final CompletionHandler<AsynchronousSocketChannel, AioServer> handler = new AcceptHandler()
    protected AsynchronousServerSocketChannel                        ssc
    @Lazy String hp = getStr('hp', ":7001")
    @Lazy Integer port = hp.split(":")[1] as Integer


    @EL(name = 'sys.starting', async = true)
    void start() {
        if (ssc) throw new RuntimeException("$name is already running")
        def cg = AsynchronousChannelGroup.withThreadPool(exec)
        ssc = AsynchronousServerSocketChannel.open(cg)
        ssc.setOption(StandardSocketOptions.SO_REUSEADDR, true)
        ssc.setOption(StandardSocketOptions.SO_RCVBUF, getInteger('so_revbuf', 64 * 1024))

        String host = hp.split(":")[0]
        def addr = new InetSocketAddress(port)
        if (host) {addr = new InetSocketAddress(host, port)}

        ssc.bind(addr, getInteger('backlog', 100))
        log.info("Start listen TCP(Aio) {}", port)
        msgFns.add {msg, se -> receive(msg, se)}
        accept()
    }


    @EL(name = 'sys.stopping', async = true)
    void stop() { ssc?.close() }


    /**
     * 添加消息处理函数
     * @param msgFn 入参1: 是消息字符串, 入参2: 回响函数
     * @return
     */
    AioServer msgFn(BiConsumer<String, AioSession> msgFn) {if (msgFn) this.msgFns << msgFn; this}


    /**
     * 消息接受处理
     * @param msg 消息内容
     * @param se AioSession
     */
    protected void receive(String msg, AioSession se) {
        log.trace("Receive client '{}' data: {}", se.sc.remoteAddress, msg)
        count() // 统计
    }


    /**
     * 接收新连接
     */
    protected void accept() {
        ssc.accept(this, handler)
    }



    /**
     * 统计每小时的处理 tcp 数据包个数
     * MM-dd HH -> 个数
     */
    protected final Map<String, LongAdder> hourCount = new ConcurrentHashMap<>(3)
    protected void count() {
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH")
        boolean isNew = false
        String hStr = sdf.format(new Date())
        LongAdder count = hourCount.get(hStr)
        if (count == null) {
            synchronized (hourCount) {
                count = hourCount.get(hStr)
                if (count == null) {
                    count = new LongAdder(); hourCount.put(hStr, count)
                    isNew = true
                }
            }
        }
        count.increment()
        if (isNew) {
            final Calendar cal = Calendar.getInstance()
            cal.setTime(new Date())
            cal.add(Calendar.HOUR_OF_DAY, -1)
            String lastHour = sdf.format(cal.getTime())
            LongAdder c = hourCount.remove(lastHour)
            if (c != null) log.info("{} 时共处理 tcp 数据包: {} 个", lastHour, c)
        }
    }


    protected class AcceptHandler implements CompletionHandler<AsynchronousSocketChannel, AioServer> {

        @Override
        void completed(final AsynchronousSocketChannel sc, final AioServer srv) {
            async {
                def rAddr = ((InetSocketAddress) sc.remoteAddress)
                srv.log.info("New TCP(AIO) Connection from: " + rAddr.hostString + ":" + rAddr.port)
                sc.setOption(StandardSocketOptions.SO_REUSEADDR, true)
                sc.setOption(StandardSocketOptions.SO_RCVBUF, 64 * 1024)
                sc.setOption(StandardSocketOptions.SO_SNDBUF, 64 * 1024)
                sc.setOption(StandardSocketOptions.SO_KEEPALIVE, true)
                def se = new AioSession(sc, srv.exec)
                msgFns?.each {se.msgFn(it)}
                se.start()
            }
            // 继续接入
            srv.accept()
        }

        @Override
        void failed(Throwable ex, AioServer srv) {
            srv.log.error(ex.message?:ex.class.simpleName, ex)
        }
    }
}

package core.aio

import cn.xnatural.enet.event.EL
import core.SchedSrv
import core.ServerTpl

import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.LongAdder
import java.util.function.BiConsumer

import static core.Utils.ipv4

/**
 * Aio 服务端
 */
class AioServer extends ServerTpl {
    protected final List<BiConsumer<String, AioSession>>                    msgFns  = new LinkedList<>()
    protected final CompletionHandler<AsynchronousSocketChannel, AioServer> handler = new AcceptHandler()
    protected AsynchronousServerSocketChannel                               ssc
    private @Lazy String                                                    hpCfg   = getStr('hp', ":7001")
    @Lazy Integer                                                           port    = hpCfg.split(":")[1] as Integer
    @Lazy def                                                               sched   = bean(SchedSrv)


    @EL(name = 'sys.starting', async = true)
    void start() {
        if (ssc) throw new RuntimeException("$name is already running")
        def cg = AsynchronousChannelGroup.withThreadPool(exec)
        ssc = AsynchronousServerSocketChannel.open(cg)
        ssc.setOption(StandardSocketOptions.SO_REUSEADDR, true)
        ssc.setOption(StandardSocketOptions.SO_RCVBUF, getInteger('so_revbuf', 64 * 1024))

        String host = hpCfg.split(":")[0]
        def addr = new InetSocketAddress(port)
        if (host) {addr = new InetSocketAddress(host, port)}

        ssc.bind(addr, getInteger('backlog', 100))
        log.info("Start listen TCP(AIO) {}", port)
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


    @EL(name = ['aio.hp', 'tcp.hp'], async = false)
    String getHp() {
        String ip = hpCfg.split(":")[0]
        if (!ip || ip == 'localhost') {ip = ipv4()}
        ip + ':' + port
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
            if (c != null) log.info("{} 时共处理 TCP(AIO) 数据包: {} 个", lastHour, c)
        }
    }


    protected class AcceptHandler implements CompletionHandler<AsynchronousSocketChannel, AioServer> {

        @Override
        void completed(final AsynchronousSocketChannel sc, final AioServer srv) {
            async {
                def rAddr = ((InetSocketAddress) sc.remoteAddress)
                srv.log.info("New TCP(AIO) Connection from: " + rAddr.hostString + ":" + rAddr.port)
                sc.setOption(StandardSocketOptions.SO_REUSEADDR, true)
                sc.setOption(StandardSocketOptions.SO_RCVBUF, getInteger('so_rcvbuf', 200 * 1024))
                sc.setOption(StandardSocketOptions.SO_SNDBUF, getInteger('so_sndbuf', 200 * 1024))
                sc.setOption(StandardSocketOptions.SO_KEEPALIVE, true)
                sc.setOption(StandardSocketOptions.TCP_NODELAY, true)

                def se = new AioSession(sc, srv)
                msgFns?.each {se.msgFn(it)}
                se.start()

                // AioSession过期 则关闭会话
                handleExpire(se)
            }
            // 继续接入新连接
            srv.accept()
        }


        protected void handleExpire(AioSession se) {
            long expire = Duration.ofMinutes(getInteger("session.maxIdle", 30)).toMillis()
            final AtomicBoolean end = new AtomicBoolean(false)
            long cur = System.currentTimeMillis()
            sched?.dyn({
                if (System.currentTimeMillis() - (se.lastUsed?:cur) > expire && end.compareAndSet(false, true)) {
                    log.info("Closing expired AioSession: " + se)
                    se.close()
                }
            }, {
                if (end.get()) return null
                long left = expire - (System.currentTimeMillis() - (se.lastUsed?:cur))
                if (left < 1000L) return new Date(System.currentTimeMillis() + (1000L * 30)) // 执行函数之前会计算下次执行的时间
                def d = new Date(System.currentTimeMillis() + (left?:0) + 10L)
                d
            })
        }


        @Override
        void failed(Throwable ex, AioServer srv) {
            srv.log.error(ex.message?:ex.class.simpleName, ex)
        }
    }
}

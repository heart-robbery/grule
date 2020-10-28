package core.aio

import cn.xnatural.enet.event.EL
import core.SchedSrv
import core.ServerTpl

import java.nio.channels.*
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.LongAdder
import java.util.function.BiConsumer

import static core.Utils.ipv4

/**
 * TCP(Aio) 服务端
 */
class AioServer extends ServerTpl {
    protected final List<BiConsumer<String, AioSession>>                    msgFns      = new LinkedList<>()
    protected final CompletionHandler<AsynchronousSocketChannel, AioServer> handler     = new AcceptHandler()
    protected AsynchronousServerSocketChannel                               ssc
    private @Lazy String                                                    hpCfg       = getStr('hp', ":7001")
    @Lazy Integer                                                           port        = hpCfg.split(":")[1] as Integer
    // 当前连接数
    protected  final Queue<AioSession>                                      connections = new ConcurrentLinkedQueue<>()


    @EL(name = 'sys.starting', async = true)
    void start() {
        if (ssc) throw new RuntimeException("$name is already running")
        def cg = AsynchronousChannelGroup.withThreadPool(exec)
        ssc = AsynchronousServerSocketChannel.open(cg)
        ssc.setOption(StandardSocketOptions.SO_REUSEADDR, true)
        ssc.setOption(StandardSocketOptions.SO_RCVBUF, getInteger('so_revbuf', 1024 * 1024))

        String host = hpCfg.split(":")[0]
        def addr = new InetSocketAddress(port)
        if (host) {addr = new InetSocketAddress(host, port)}

        ssc.bind(addr, getInteger('backlog', 128))
        log.info("Start listen TCP(AIO) {}", port)
        msgFns.add {msg, se -> receive(msg, se)}
        accept()
    }


    @EL(name = 'sys.stopping', async = true)
    void stop() { ssc?.close() }


    @EL(name = 'sys.started', async = true)
    protected void started() {
        bean(SchedSrv)?.cron(getStr("0 */10 * * * ?", "cleanCron")) {clean()}
    }


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


    /**
     * 清除已关闭或已过期的连接
     */
    protected void clean() {
        if (connections.isEmpty()) return
        long expire = Duration.ofMinutes(getInteger("aioSession.maxIdle",
            connections.size() > 100 ? 3 : (connections.size() > 50 ? 5 : (connections.size() > 30 ? 10 : 20))
        )).toMillis()

        for (def itt = connections.iterator().iterator(); itt.hasNext(); ) {
            def se = itt.next()
            if (se == null) break
            if (!se.sc.isOpen()) {
                connections.remove(se)
                se.close()
                log.debug("Cleaned unavailable AioSession: " + se + ", connected: " + connections.size())
            } else if (System.currentTimeMillis() - se.lastUsed > expire) {
                connections.remove(se)
                se.close()
                log.debug("Closed expired AioSession: " + se + ", connected: " + connections.size())
                break
            }
        }
    }


    protected class AcceptHandler implements CompletionHandler<AsynchronousSocketChannel, AioServer> {

        @Override
        void completed(final AsynchronousSocketChannel sc, final AioServer srv) {
            async {
                def rAddr = ((InetSocketAddress) sc.remoteAddress)
                srv.log.info("New TCP(AIO) Connection from: " + rAddr.hostString + ":" + rAddr.port + ", connected: " + connections.size())
                sc.setOption(StandardSocketOptions.SO_REUSEADDR, true)
                sc.setOption(StandardSocketOptions.SO_RCVBUF, getInteger('so_rcvbuf', 1024 * 1024 * 2))
                sc.setOption(StandardSocketOptions.SO_SNDBUF, getInteger('so_sndbuf', 1024 * 1024 * 2))
                sc.setOption(StandardSocketOptions.SO_KEEPALIVE, true)
                sc.setOption(StandardSocketOptions.TCP_NODELAY, true)

                def se = new AioSession(sc, srv); connections.offer(se)
                msgFns?.each {se.msgFn(it)}
                se.closeFn = {connections.remove(se)}
                se.start()
            }
            // 继续接入新连接
            srv.accept()
        }


        @Override
        void failed(Throwable ex, AioServer srv) {
            if (ex !instanceof ClosedChannelException) {
                srv.log.error(ex.message?:ex.class.simpleName, ex)
            }
        }
    }
}

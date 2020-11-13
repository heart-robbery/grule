package core.aio

import cn.xnatural.enet.event.EL
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
    /**
     * 接收TCP消息的处理函数集
     */
    protected final List<BiConsumer<String, AioSession>>                    msgFns      = new LinkedList<>()
    /**
     * 新TCP连接 接受处理
     */
    protected final CompletionHandler<AsynchronousSocketChannel, AioServer> acceptor    = new AcceptHandler()
    protected AsynchronousServerSocketChannel                               ssc
    /**
     * 绑定配置 hp -> host:port
     */
    protected  @Lazy String                                                 hpCfg       = getStr('hp', ":7001")
    @Lazy Integer                                                           port        = {
        try {
            return hpCfg.split(":")[1] as Integer
        } catch (ex) {
            throw new Exception("${name}.hp 格式错误. $hpCfg".toString(), ex)
        }
    }()
    // 当前连会话
    protected  final Queue<AioSession>                                      connections = new ConcurrentLinkedQueue<>()


    /**
     * 启动
     */
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


    /**
     * 关闭
     */
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
        ssc.accept(this, acceptor)
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
     * 接收系统心跳事件
     */
    @EL(name = "sys.heartbeat", async = true)
    protected void clean() {
        if (connections.isEmpty()) return
        int size = connections.size()
        long expire = Duration.ofSeconds(getInteger("aioSession.maxIdle",
            {
                if (size > 80) return 60
                if (size > 50) return 120
                if (size > 30) return 180
                if (size > 20) return 300
                if (size > 10) return 400
                return 600
            }()
        )).toMillis()

        int limit = {
            if (size > 80) return 8
            if (size > 50) return 5
            if (size > 30) return 3
            return 2
        }()
        for (def itt = connections.iterator(); itt.hasNext() && limit > 0; ) {
            def se = itt.next()
            if (se == null) break
            if (!se.sc.isOpen()) {
                itt.remove(); se.close()
                log.info("Cleaned unavailable AioSession: " + se + ", connected: " + connections.size())
            } else if (System.currentTimeMillis() - se.lastUsed > expire) {
                limit--; itt.remove(); se.close()
                log.info("Closed expired AioSession: " + se + ", connected: " + connections.size())
            }
        }
    }


    /**
     * 连接处理器
     */
    protected class AcceptHandler implements CompletionHandler<AsynchronousSocketChannel, AioServer> {

        @Override
        void completed(final AsynchronousSocketChannel sc, final AioServer srv) {
            async {
                def rAddr = ((InetSocketAddress) sc.remoteAddress)
                sc.setOption(StandardSocketOptions.SO_REUSEADDR, true)
                sc.setOption(StandardSocketOptions.SO_RCVBUF, getInteger('so_rcvbuf', 1024 * 1024 * 2))
                sc.setOption(StandardSocketOptions.SO_SNDBUF, getInteger('so_sndbuf', 1024 * 1024 * 2))
                sc.setOption(StandardSocketOptions.SO_KEEPALIVE, true)
                sc.setOption(StandardSocketOptions.TCP_NODELAY, true)

                def se = new AioSession(sc, srv); connections.offer(se)
                msgFns?.each {se.msgFn(it)}
                srv.log.info("New TCP(AIO) Connection from: " + rAddr.hostString + ":" + rAddr.port + ", connected: " + connections.size())
                se.start()
                if (connections.size() > 10) clean()
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

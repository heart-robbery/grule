package core.module.http

import cn.xnatural.enet.event.EL
import core.module.ServerTpl

import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.text.SimpleDateFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

class HttpServer extends ServerTpl {
    protected final CompletionHandler<AsynchronousSocketChannel, HttpServer> handler = new AcceptHandler()
    protected       AsynchronousServerSocketChannel                         ssc
    @Lazy           String                                                  hp      = getStr('hp', ":9090")
    @Lazy           Integer                                                 port    = hp.split(":")[1] as Integer
    protected final Dispatcher dispatcher = new Dispatcher()


    HttpServer(String name) { super(name) }
    HttpServer() { super('web') }


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
        log.info("Start listen HTTP(AIO) {}", port)
        accept()
    }


    @EL(name = 'sys.stopping', async = true)
    void stop() { ssc?.close() }


    /**
     * 接收新的 http 请求
     * @param request
     * @param session
     */
    protected void receive(HttpRequest request, HttpSession session) {
        log.info("Start Request '{}': {}. from: " + session.sc.remoteAddress.toString(), request.id, request.rowUrl)
        count()
    }


    /**
     * 接收新连接
     */
    protected void accept() {
        ssc.accept(this, handler)
    }



    /**
     * 统计每小时的处理 http 数据包个数
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
            if (c != null) log.info("{} 时共处理 http 请求: {} 个", lastHour, c)
        }
    }


    protected class AcceptHandler implements CompletionHandler<AsynchronousSocketChannel, HttpServer> {

        @Override
        void completed(final AsynchronousSocketChannel sc, final HttpServer srv) {
            async {
                def rAddr = ((InetSocketAddress) sc.remoteAddress)
                srv.log.debug("New HTTP(AIO) Connection from: " + rAddr.hostString + ":" + rAddr.port)
                sc.setOption(StandardSocketOptions.SO_REUSEADDR, true)
                sc.setOption(StandardSocketOptions.SO_RCVBUF, 64 * 1024)
                sc.setOption(StandardSocketOptions.SO_SNDBUF, 64 * 1024)
                sc.setOption(StandardSocketOptions.SO_KEEPALIVE, true)
                def se = new HttpSession(sc, srv.exec)
                se.handler = {req -> receive(req, se)}
                se.start()
            }
            // 继续接入
            srv.accept()
        }

        @Override
        void failed(Throwable ex, HttpServer srv) {
            srv.log.error(ex.message?:ex.class.simpleName, ex)
        }
    }
}

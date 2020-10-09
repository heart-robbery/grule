package core.aio

import cn.xnatural.enet.event.EL
import core.ServerTpl

import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.BiConsumer

/**
 * TCP(AIO) 客户端
 */
class AioClient extends ServerTpl {
    protected final List<BiConsumer<String, AioSession>> msgFns = new LinkedList<>()
    protected final Map<String, List<AioSession>> ses    = new ConcurrentHashMap<>()
    @Lazy protected AsynchronousChannelGroup group = AsynchronousChannelGroup.withThreadPool(exec)


    @EL(name = 'sys.stopping', async = true)
    void stop() {
        ses?.each {it.value?.each {se -> se.close()}}
    }


    /**
     * 添加消息处理函数
     * @param msgFn 入参1: 是消息字符串, 入参2: AioSession
     * @return
     */
    AioClient msgFn(BiConsumer<String, AioSession> msgFn) {if (msgFn) this.msgFns << msgFn; this}


    /**
     * 发送消息
     * @param host 主机名
     * @param port 端口
     * @param msg 消息内容
     */
    AioClient send(String host, Integer port, String msg) {
        getSession(host, port).send(msg)
        this
    }


    /**
     * 获取AioSession
     * @param host
     * @param port
     * @return
     */
    protected AioSession getSession(String host, Integer port) {
        host = host.trim()
        String key = host+":"+port
        AioSession se = null
        List<AioSession> ls = ses.get(key)
        if (ls == null) {
            synchronized (this) {
                ls = ses.get(key)
                if (ls == null) {
                    ls = safeList(); ses.put(key, ls)
                    se = create(host, port); ls.add(se)
                }
            }
        }

        ls.findAll {!it.sc.isOpen()}.each {it.close()}
        se = ls.find {!it.busy()}

        if (se == null) {
            se = create(host, port); ls.add(se)
        }

        return se
    }


    /**
     * 安全列表
     * @return
     */
    protected List<AioSession> safeList() {
        final ReadWriteLock lock = new ReentrantReadWriteLock()
        new LinkedList<AioSession>() {
            @Override
            AioSession get(int index) {
                try {
                    lock.readLock().lock()
                    return super.get(index)
                } finally {
                    lock.readLock().unlock()
                }
            }

            @Override
            boolean contains(Object o) {
                try {
                    lock.readLock().lock()
                    return super.contains(o)
                } finally {
                    lock.readLock().unlock()
                }
            }

            @Override
            boolean remove(Object o) {
                try {
                    lock.writeLock().lock()
                    return super.remove(o)
                } finally {
                    lock.writeLock().unlock()
                }
            }

            @Override
            boolean add(AioSession e) {
                try {
                    lock.writeLock().lock()
                    return super.add(e)
                } finally {
                    lock.writeLock().unlock()
                }
            }
        }
    }


    /**
     * 创建 AioSession
     * @param host 目标主机
     * @param port 目标端口
     * @return {@link AioSession}
     */
    protected AioSession create(String host, Integer port) {
        String key = "$host:$port"
        // 创建连接
        final def sc = AsynchronousSocketChannel.open(group)
        sc.setOption(StandardSocketOptions.TCP_NODELAY, true)
        sc.setOption(StandardSocketOptions.SO_REUSEADDR, true)
        sc.setOption(StandardSocketOptions.SO_KEEPALIVE, true)
        sc.setOption(StandardSocketOptions.SO_RCVBUF, getInteger('so_rcvbuf', 1024 * 1024 * 2))
        sc.setOption(StandardSocketOptions.SO_SNDBUF, getInteger('so_sndbuf', 1024 * 1024 * 2))
        // asc.bind(new InetSocketAddress('localhost', bean(AioServer).port))
        try {
            sc.connect(new InetSocketAddress(host, port)).get(getLong("aioConnectTimeout", 3000L), TimeUnit.MILLISECONDS)
            log.info("New TCP(AIO) connection to " + key)
        } catch(ex) {
            sc.close()
            throw new Exception("连接错误. $key", ex)
        }
        def se = new AioSession(sc, this)
        msgFns?.each {se.msgFn(it)}
        se.closeFn = {ses.get(key).remove(se)}
        se.start()
        se
    }
}

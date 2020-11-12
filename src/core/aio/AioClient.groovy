package core.aio

import cn.xnatural.enet.event.EL
import core.ServerTpl
import core.Utils

import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.ClosedChannelException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * TCP(AIO) 客户端
 */
class AioClient extends ServerTpl {
    protected final List<BiConsumer<String, AioSession>>    msgFns     = new LinkedList<>()
    protected final Map<String, Utils.SafeList<AioSession>> sessionMap = new ConcurrentHashMap<>()
    @Lazy protected AsynchronousChannelGroup                group      = AsynchronousChannelGroup.withThreadPool(exec)


    @EL(name = 'sys.stopping', async = true)
    void stop() {
        sessionMap?.each {it.value?.each { se -> se.close()}}
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
     * @param failFn 失败回调
     * @param okFn 成功回调
     */
    AioClient send(String host, Integer port, String msg, Consumer<Exception> failFn = null, Consumer<AioSession> okFn = null) {
        final sessionSupplier = new Supplier<AioSession>() {
            @Override
            AioSession get() {
                try {
                    return getSession(host, port)
                } catch (ex) {
                    failFn?.accept(ex)
                }
                null
            }
        }
        final Consumer<Exception> subFailFn = new Consumer<Exception>() {
            @Override
            void accept(Exception ex) {
                if (ex instanceof ClosedChannelException) { // 连接关闭时 重试
                    AioSession se = sessionSupplier.get()
                    if (se) {
                        se.send(msg, this, {okFn?.accept(se)})
                    }
                }
            }
        }
        AioSession se = sessionSupplier.get()
        if (se) {
            se.send(msg, subFailFn, {okFn?.accept(se)})
        }
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
        def ls = sessionMap.get(key)
        if (ls == null) {
            synchronized (sessionMap) {
                ls = sessionMap.get(key)
                if (ls == null) {
                    ls = Utils.safelist(AioSession); sessionMap.put(key, ls)
                    se = create(host, port); ls.add(se)
                }
            }
        }

        se = ls.findAny {!it.busy()}
        if (se == null) {
            se = create(host, port); ls.add(se)
        }

        return se
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
        try {
            sc.connect(new InetSocketAddress(host, port)).get(getLong("aioConnectTimeout", 3000L), TimeUnit.MILLISECONDS)
            log.info("New TCP(AIO) connection to " + key)
        } catch(ex) {
            try {sc.close()} catch(exx) {}
            throw new Exception("连接错误. $key", ex)
        }
        def se = new AioSession(sc, this)
        msgFns?.each {se.msgFn(it)}
        se.closeFn = {sessionMap.get(key).remove(se)}
        se.start()
        se
    }
}

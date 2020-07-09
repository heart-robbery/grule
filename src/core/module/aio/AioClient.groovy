package core.module.aio

import cn.xnatural.enet.event.EL
import core.module.ServerTpl

import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

class AioClient extends ServerTpl {
    protected final List<Function<String, String>>    msgFns = new LinkedList<>()
    protected final Map<String, List<AioSession>> ses    = new ConcurrentHashMap<>()
    // 别名 -> [host:port]
    protected final Map<String, List<String>> alias    = new ConcurrentHashMap<>()


    @EL(name = 'sys.stopping', async = true)
    void stop() {
        ses?.each {it.value?.each {se -> se.stop()}}
    }


    AioClient msgFn(Function<String, String> msgFn) {if (msgFn) this.msgFns.add(msgFn); this}


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
     * 发送消息
     * @param appName 应用名
     * @param msg 消息内容
     */
    AioClient send(String appName, String msg) {
        def ls = alias.get(appName)
        assert ls : "未找到appName的连接配置"
        getSession(ls.get(new Random().nextInt(ls.size()))).send(msg)
        this
    }


    /**
     * 应用名 对应多个 host:port
     * @param appName 应用名
     * @param host 主机名
     * @param port 端口
     * @return
     */
    AioClient alias(String appName, String host, Integer port) {
        String key = host+":"+port
        def ls = alias.computeIfAbsent(appName, {s -> new LinkedList<>() })
        if (!ls.contains(key)) ls.add(key)
        this
    }


    protected AioSession getSession(String host, Integer port) {
        def ls = ses.computeIfAbsent(host+":"+port, {s ->
            AsynchronousChannelGroup group = AsynchronousChannelGroup.withThreadPool(exec)
            def sc = AsynchronousSocketChannel.open(group)
            sc.setOption(StandardSocketOptions.TCP_NODELAY, true)
            sc.setOption(StandardSocketOptions.SO_REUSEADDR, true)
            sc.setOption(StandardSocketOptions.SO_KEEPALIVE, true)
            // asc.bind(new InetSocketAddress('localhost', bean(AioServer).port))
            sc.connect(new InetSocketAddress(host, port))
            def se = new AioSession(sc, exec)
            msgFns?.each {se.addHandler(it)}
            List<AioSession> ls = new LinkedList<>()
            ls.add(se)
            return ls
        })
        ls.get(new Random().nextInt(ls.size()))
    }
}

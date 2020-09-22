package core.aio

import java.time.Duration
import java.util.function.BiConsumer
import java.util.function.Consumer

/**
 * Aio 消息发送对象
 */
class AioMsg {
    /**
     * 消息内容
     */
    String                content
    /**
     * 发送超时
     */
    Duration              timeout
    /**
     * 发送成功回调
     */
    BiConsumer<AioMsg, AioSession>      okCallback
    /**
     * 发送失败回调
     */
    BiConsumer<Throwable, AioSession> failCallback
    Integer               retryCount = 0


    static AioMsg of(String msg, Duration timeout = null, Consumer<AioMsg> okCallback = null, Consumer<Throwable> failCallback = null) {
        new AioMsg(content: msg, timeout: timeout, okCallback: okCallback, failCallback: failCallback)
    }

    static AioMsg of(String msg, Consumer<AioMsg> okCallback) {
        new AioMsg(content: msg, timeout: null, okCallback: okCallback, failCallback: null)
    }
}

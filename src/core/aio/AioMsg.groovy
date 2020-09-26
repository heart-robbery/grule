package core.aio

import java.time.Duration
import java.util.function.BiConsumer

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


    static AioMsg of(String msg, Duration timeout = null, BiConsumer<AioMsg, AioSession> okCallback = null, BiConsumer<Throwable, AioSession> failCallback = null) {
        new AioMsg(content: msg, timeout: timeout, okCallback: okCallback, failCallback: failCallback)
    }

    static AioMsg of(String msg, BiConsumer<AioMsg, AioSession> okCallback) {
        new AioMsg(content: msg, timeout: null, okCallback: okCallback, failCallback: null)
    }
}

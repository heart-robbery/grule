package ctrl.ratpack


import core.Utils
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpVersion
import ratpack.handling.Handler
import ratpack.registry.Registry
import ratpack.server.internal.NettyHandlerAdapter

import static io.netty.handler.codec.http.HttpResponseStatus.SERVICE_UNAVAILABLE

class RatpackHander extends NettyHandlerAdapter {
    RatpackWeb rw

    RatpackHander(RatpackWeb rw, Registry serverRegistry, Handler handler) throws Exception {
        super(serverRegistry, handler)
        this.rw = rw
    }


    @Override
    void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            int i = rw.devourer.getWaitingCount()
            // 当请求对列中等待处理的请求过多就拒绝新的请求(默认值: 线程池的线程个数的3倍)
            if (i >= (rw.attrs.maxWaitRequest?:(Utils.findMethod(rw.exec.getClass(), "getCorePoolSize").invoke(rw.exec) * 3))) {
                if (i > 0 && i % 3 == 0) rw.log.warn("There are currently {} requests waiting to be processed.", i)
                rw.log.warn("Drop request: {}", msg.uri())
                ctx.writeAndFlush(new DefaultHttpResponse(HttpVersion.HTTP_1_1, SERVICE_UNAVAILABLE))
            } else {
                rw.devourer.offer{rw.exec.execute{rw.count(); super.channelRead(ctx, msg) }}
            }
        } else {
            super.channelRead(ctx, msg)
        }
    }
}

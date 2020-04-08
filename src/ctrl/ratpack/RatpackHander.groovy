package ctrl.ratpack


import groovy.transform.PackageScope
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpVersion
import ratpack.handling.Handler
import ratpack.registry.Registry
import ratpack.server.internal.NettyHandlerAdapter

import static io.netty.handler.codec.http.HttpResponseStatus.SERVICE_UNAVAILABLE

@PackageScope
class RatpackHander extends NettyHandlerAdapter {
    RatpackWeb rw

    RatpackHander(RatpackWeb rw, Registry serverRegistry, Handler handler) throws Exception {
        super(serverRegistry, handler)
        this.rw = rw
    }


    @Override
    void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            int i = rw.queue().waitingCount
            // 当请求对列中等待处理的请求过多就拒绝新的请求(默认值: 线程池的线程个数的3倍)
            if (i >= rw.getInteger("maxWaitRequest", ((Integer) rw.exec["getCorePoolSize"]) * 3) ||
                ((Integer) rw.exec["waitingCount"]) > ((Integer) rw.exec["getCorePoolSize"]) * 2
            ) {
                if (i > 0 && i % 3 == 0) rw.log.warn("There are currently {} requests waiting to be processed.", i)
                rw.log.warn("Drop request: {}", msg.uri())
                ctx.writeAndFlush(new DefaultHttpResponse(HttpVersion.HTTP_1_1, SERVICE_UNAVAILABLE))
            } else {
                // 请求入对执行
                rw.queue().offer{ super.channelRead(ctx, msg) }
            }
        } else {
            super.channelRead(ctx, msg)
        }
    }
}

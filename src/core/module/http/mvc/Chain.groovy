package core.module.http.mvc

import core.module.http.HttpContext
import core.module.http.HttpServer
import org.slf4j.LoggerFactory

import java.util.function.Consumer

class Chain {
    protected static final def log = LoggerFactory.getLogger(Chain)
    protected final LinkedList<Handler> handlers = new LinkedList<>()
    protected final HttpServer server


    Chain(HttpServer server) { this.server = server }


    Chain add(Handler handler) {
        // 按优先级添加, 相同类型比较, FilterHandler > PathHandler
        boolean added = false
        for (def it = handlers.listIterator(); it.hasNext(); ) {
            def h = it.next()
            // order 值越大越排前面, 相同的按顺序排
            if ((h.class == handler.class && h.order() < handler.order())) {
                added = true
                it.previous()
                it.add(handler)
                break
            }
        }
        if (!added) {
            if (handler instanceof FilterHandler) handlers.offerFirst(handler)
            else handlers.offerLast(handler)
        }
        this
    }


    /**
     * 执行此Chain
     * @param ctx
     */
    protected void handle(HttpContext ctx) {
        boolean match = false
        for (Handler h: handlers) {
            if (h instanceof FilterHandler) {
                h.handle(ctx)
            } else if (h instanceof PathHandler) {
                match = h.match(ctx)
                log.trace((match ? 'Matched' : 'Unmatch') + " {}, {}", h.path(), ctx.request.path)
                if (match) {
                    h.handle(ctx)
                    break
                }
            } else new RuntimeException('未处理Handler类型: ' + h.class.simpleName)
            // 退出条件
            if (ctx.response.commit.get()) break
        }
        if (ctx.response.commit.get()) return
        if (!match) { // 未找到匹配
            ctx.response.statusIfNotSet(404)
            ctx.render()
        }
        else if (ctx.response.status != null) { // 已经设置了status
            ctx.render()
        }
    }


    /**
     * 指定方法,路径处理器
     * @param method get, post ...
     * @param path 匹配路径
     * @param handler 处理器
     * @return
     */
    Chain method(String method, String path, Handler handler) {
        add(new PathHandler() {
            @Override
            void handle(HttpContext ctx) {
                try {
                    handler.handle(ctx)
                } catch (ex) {server.errHandle(ex, ctx)}
            }

            @Override
            String path() { path }

            @Override
            boolean match(HttpContext ctx) {
                boolean f = super.match(ctx)
                if (method && !method.equalsIgnoreCase(ctx.request.method)) {
                    ctx.response.status(415)
                    return false
                }
                if (415 == ctx.response.status) ctx.response.status(200)
                return f
            }
        })
    }


    /**
     * 添加Filter, 默认匹配
     * @param handler
     * @return
     */
    Chain filter(Handler handler, float order = 0) {
        add(new FilterHandler() {
            @Override
            void handle(HttpContext ctx) {
                try {
                    handler.handle(ctx)
                } catch (ex) {server.errHandle(ex, ctx)}
            }

            @Override
            float order() { order }
        })
    }


    Chain path(String path, Handler handler) {
        method(null, path, handler)
    }


    Chain get(String path, Handler handler) {
        method('get', path, handler)
    }


    Chain post(String path, Handler handler) {
        method('post', path, handler)
    }


    Chain prefix(String prefix, Consumer<Chain> handlerBuilder) {
        prefix = Handler.extract(prefix)
        add(new PathHandler() {
            final Chain chain = {
                Chain c = new Chain(server)
                handlerBuilder.accept(c)
                c
            }()

            @Override
            String path() { return prefix }

            @Override
            boolean match(HttpContext ctx) {
                boolean f = ctx.pieces[0] == prefix
                if (f) ctx.pieces = ctx.pieces.drop(1)
                f
            }

            @Override
            void handle(HttpContext ctx) {
                chain.handle(ctx)
            }
        })
    }
}

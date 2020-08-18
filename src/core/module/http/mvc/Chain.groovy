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
        int i = 0
        for (def it = handlers.listIterator(); it.hasNext(); ) {
            def h = it.next()
            if (h.type() == handler.type()) {
                if (h.order() < handler.order()) { // order 值越大越排前面, 相同的按顺序排
                    it.previous() // 相同类型 不是第一个 插入前面, 第一个插在第一个后面
                    it.add(handler); added = true; break
                } else if (h.order() == handler.order()) {
                    it.add(handler); added = true; break
                } else { // 小于
                    if (i == 0 && it.hasNext() && (h = it.next())) { // 和handler同类型的只有一个时, 插在后边
                        if (h.type() != handler.type()) {
                            it.previous()
                            it.add(handler); added = true; break
                        } else it.previous()
                    }
                }
                i++
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
     * @param contentType application/json, multipart/form-data, application/x-www-form-urlencoded
     * @param handler 处理器
     * @return
     */
    Chain method(String method, String path, String contentType = null, Handler handler) {
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
                if (contentType && !contentType.split(';')[0].equalsIgnoreCase(ctx.request.contentType.split(';')[0])) {
                    ctx.response.status(405)
                    return false
                }
                if (415 == ctx.response.status || 405 == ctx.response.status) ctx.response.status(200)
                return f
            }
        })
    }


    /**
     * 添加 websocket Handler
     * @return
     */
    Chain ws(String path, Handler handler) {
        add(new WSHandler() {
            @Override
            void handle(HttpContext ctx) {
                try {
                    handler.handle(ctx)
                } catch (ex) {server.errHandle(ex, ctx)}
            }

            @Override
            String path() { path }
        })
    }


    /**
     * 添加Filter, 默认匹配
     * @param handler
     * @return
     */
    Chain filter(Handler handler, int order = 0) {
        add(new FilterHandler() {
            @Override
            void handle(HttpContext ctx) {
                try {
                    handler.handle(ctx)
                } catch (ex) {server.errHandle(ex, ctx)}
            }

            @Override
            double order() { Double.valueOf(order) }
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


    /**
     * 前缀
     * @param prefix
     * @param handlerBuilder
     * @return Chain
     */
    Chain prefix(String prefix, Consumer<Chain> handlerBuilder) {
        prefix = Handler.extract(prefix)
        add(new PathHandler() {
            final Chain chain = {
                Chain c = new Chain(server)
                handlerBuilder.accept(c)
                c
            }()

            @Override
            String path() { prefix }

            @Override
            boolean match(HttpContext ctx) {
                boolean f = false
                for (int i = 0; i < pieces.length; i++) {
                    f = (pieces[i] == ctx.pieces[i])
                    if (!f) break
                }
                if (f) ctx.pieces = ctx.pieces.drop(pieces.length)
                f
            }

            @Override
            void handle(HttpContext ctx) {
                chain.handle(ctx)
            }
        })
    }
}

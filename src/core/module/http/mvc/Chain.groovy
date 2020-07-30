package core.module.http.mvc

import core.module.http.HttpContext

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

class Chain {

    // path 前缀
    protected String prefix
    protected final LinkedList<Handler> handlers = new LinkedList<>()
    protected Consumer<Exception> errorHandler


    /**
     * 执行此Chain
     * @param ctx
     */
    protected void handle(HttpContext ctx) {
        boolean match = false
        for (Handler h: handlers) {
            match = h.match(ctx)
            if (match) h.handle(ctx)
            // 找到完全匹配的handler 则退出
            if ((ctx.response.status() != null) || (match && h.path() != null) || ctx.response.commit.get()) break
        }
        if (!match) {
            if (ctx.response.status() == null) ctx.response.status(404)
            ctx.render(null)
        }
        else if (ctx.response.status() != null && !ctx.response.commit.get()) {
            ctx.render(null)
        }
    }


    Chain all(final Handler handler) {
        Handler wrapper = new Handler() { // 包装异常处理
            @Override
            void handle(HttpContext ctx) {
                try {
                    handler.handle(ctx)
                } catch (ex) {
                    if (errorHandler) {
                        errorHandler.accept(ex)
                        ctx.close()
                    }
                    else {
                        ctx.close()
                        throw ex
                    }
                }
            }
        }
        if (wrapper.path() == null) handlers.add(wrapper)
        else {
            boolean added = false
            for (def it = handlers.listIterator(); it.hasNext(); ) {
                def h = it.next()
                if (h.priority > wrapper.priority) { // priority 值越小越排前面, 相同的按顺序排
                    added = true
                    it.add(wrapper)
                }
            }
            if (!added) {
                handlers.add(wrapper)
            }
        }
        this
    }


    Chain path(String path, Handler handler) {
        all(
            new Handler() {
                @Override
                void handle(HttpContext ctx) {
                    handler.handle(ctx)
                }

                @Override
                String path() { return (prefix == null ? '' : prefix + "/") + path }
            }
        )
        this
    }


    Chain get(String path, Handler handler) {
        path(path, new Handler() {
            @Override
            void handle(HttpContext ctx) {
                handler.handle(ctx)
            }
            @Override
            boolean match(HttpContext ctx) {
                boolean f = super.match(ctx)
                if (!'get'.equalsIgnoreCase(ctx.request.method)) {
                    ctx.response.status(415)
                    return false
                }
                return f
            }
        })
        this
    }


    Chain post(String path, Handler handler) {
        path(path, new Handler() {
            @Override
            void handle(HttpContext ctx) {
                handler.handle(ctx)
            }
            @Override
            boolean match(HttpContext ctx) {
                boolean f = super.match(ctx)
                if (!'post'.equalsIgnoreCase(ctx.request.method)) {
                    ctx.response.status(415)
                    return false
                }
                return f
            }
        })
        this
    }


    Chain prefix(String prefix, Consumer<Chain> handlerBuilder) {
        prefix = Handler.extract(prefix)
        all(new Handler() {
            final Chain chain = {
                Chain c = new Chain()
                handlerBuilder.accept(c)
                c.prefix = prefix
                c
            }()

            @Override
            String path() { return prefix }

            @Override
            boolean match(HttpContext ctx) {
                return extract(ctx.request.path).split('/')[0] == prefix
            }

            @Override
            void handle(HttpContext ctx) {
                chain.handle(ctx)
            }
        })
        this
    }



    /**
     * 路径匹配
     * @param pathTpl 路径模板
     * @param path 请求路径
     * @return 是否匹配
     */
    protected final Map<String, Map<String, Boolean>> matchResult = new ConcurrentHashMap<>()
    boolean match(String pathTpl, String path) {
        Map<String, Boolean> cache = matchResult.computeIfAbsent(pathTpl, {new ConcurrentHashMap<>()})
        Boolean match = cache.get(path)
        if (match == null) {
            path = prefix == null ? path : path.replace(prefix + '/', '')
            def arr1 = path.split("/")
            def arr2 = pathTpl.split("/")
        }
        match
    }


    protected String[] split(String path) {
        if (path.endsWith("/")) path = path.substring(0, path.length() - 2)
        if (path.startsWith("/")) path = path.substring(1)
        path.split("/")
    }
}

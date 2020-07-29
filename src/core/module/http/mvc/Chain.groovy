package core.module.http.mvc

import core.module.http.HttpContext

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

class Chain {

    // path 前缀
    protected String prefix
    protected final LinkedList<Handler> handlers = new LinkedList<>()


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
            if ((match && h.path() != null) || ctx.response.commit.get()) break
        }
        if (!match) {
            ctx.response.status(404)
            ctx.render(null)
        }
    }


    Chain all(Handler handler) {
        if (handler.path() == null) handlers.add(handler)
        else {
            boolean added = false
            for (def it = handlers.listIterator(); it.hasNext(); ) {
                def h = it.next()
                if (h.priority > handler.priority) { // priority 值越小越排前面, 相同的按顺序排
                    added = true
                    it.add(handler)
                }
            }
            if (!added) {
                handlers.add(handler)
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
        all(new Handler() {
            @Override
            void handle(HttpContext ctx) {
                handler.handle(ctx)
            }
            @Override
            boolean match(HttpContext ctx) {
                if (!'get'.equalsIgnoreCase(ctx.request.method)) {
                    return false
                }
                return super.match(ctx)
            }
            @Override
            String path() { return (prefix == null ? '' : prefix + "/") + path }
        })
        this
    }


    Chain post(String path, Handler handler) {
        all(new Handler() {
            @Override
            void handle(HttpContext ctx) {
                handler.handle(ctx)
            }
            @Override
            boolean match(HttpContext ctx) {
                if (!'post'.equalsIgnoreCase(ctx.request.method)) {
                    return false
                }
                return super.match(ctx)
            }
            @Override
            String path() { return (prefix == null ? '' : prefix + "/") + path }
        })
        this
    }


    Chain prefix(String prefix, Consumer<Chain> handlerBuilder) {
        prefix = Handler.extract(prefix)
        all(new Handler() {
            @Lazy Chain chain = {
                Chain c = new Chain()
                handlerBuilder.accept(c)
                c.prefix = prefix
                c
            }

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

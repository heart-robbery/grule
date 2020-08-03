package core.module.http.mvc

import core.module.http.HttpContext

abstract class Handler {


    /**
     * 逻辑处理
     * @param ctx
     */
    abstract void handle(HttpContext ctx)


    String path() {null}


    // 路径块. /test/pp -> ['test', 'pp']
    @Lazy String[] pieces = {
        def p = path()
        if (p == null) return null
        extract(p).split('/')
    }()

    // 匹配的先后顺序, 越小越先匹配
    @Lazy int priority = {
        if (pieces == null) return 0
        int i = 0
        for (String piece: pieces) {
            if (isVariable(piece)) i++
        }
        return i
    }()


    /**
     * 匹配
     * @param ctx
     * @return
     */
    boolean match(HttpContext ctx) {
        if (path() == null) return true
        if (pieces.length != ctx.pieces.length) return false
        for (int i = 0; i < pieces.length; i++) {
            if (pieces[i].startsWith(":")) {
                int index = pieces[i].indexOf('.')
                if (index == -1) ctx.pathToken.put(pieces[i].substring(1), ctx.pieces[i])
                else ctx.pathToken.put(pieces[i].substring(1, index), ctx.pieces[i].substring(0, index - 1))
            } else if (pieces[i] != ctx.pieces[i]) {
                ctx.pathToken.clear()
                return false
            }
        }
        true
    }


    /**
     * 路径中的分片 是否为可变变量字符串
     * @param piece
     * @return
     */
    protected static boolean isVariable(String piece) {
        if (piece.startsWith(":")) return true
        if (piece.startsWith("~")) return true
        false
    }


    /**
     * 去掉 路径 前后 的 /
     * @param path
     * @return
     */
    static String extract(String path) {
        if (path.endsWith("/")) path = path.substring(0, path.length() - 2)
        if (path.startsWith("/")) path = path.substring(1)
        return path
    }
}

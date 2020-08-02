package core.module.http.mvc

import core.module.http.HttpContext

abstract class Handler {


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
        String[] pieces2 = ctx.pieces
        if (pieces.length != pieces2.length) return false
        for (int i = 0; i < pieces.length; i++) {
            if (pieces[i].startsWith(":")) {
                int index = pieces[i].indexOf('.')
                if (index == -1) ctx.pathToken.put(pieces[i].substring(1), pieces2[i])
                else ctx.pathToken.put(pieces[i].substring(1, index), pieces2[i])
            } else if (pieces[i] != pieces2[i]) {
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


    static String extract(String path) {
        if (path.endsWith("/")) path = path.substring(0, path.length() - 2)
        if (path.startsWith("/")) path = path.substring(1)
        return path
    }
}

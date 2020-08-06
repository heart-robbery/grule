package core.module.http.mvc

import core.module.http.HttpContext

abstract class PathHandler extends Handler {

    String path() {null}


    // 路径块. /test/pp -> ['test', 'pp']
    @Lazy String[] pieces = {
        def p = path()
        if (p == null) throw new IllegalArgumentException('PathHandler path must not be null')
        extract(p).split('/')
    }()

    // 匹配的先后顺序, 越大越先匹配
    @Lazy float priority = {
        if (pieces == null) return Float.MAX_VALUE
        float i = pieces.length
        for (String piece: pieces) {
            if (piece.startsWith(":")) {
                if (piece.indexOf('.') > 0) i += 0.01
                continue
            } else if (piece.startsWith('~')) {
                i += 0.001
                continue
            }
            i += 0.1
        }
        return i
    }()


    @Override
    float order() {priority}


    @Override
    boolean match(HttpContext ctx) {
        if (pieces.length != ctx.pieces.length) return false
        for (int i = 0; i < pieces.length; i++) {
            if (pieces[i].startsWith(":")) {
                int index = pieces[i].indexOf('.')
                if (index == -1) ctx.pathToken.put(pieces[i].substring(1), ctx.pieces[i])
                else {
                    int index2 = ctx.pieces[i].indexOf('.')
                    if (index2 > 0 && pieces[i].substring(index) == ctx.pieces[i].substring(index2)) {
                        ctx.pathToken.put(pieces[i].substring(1, index), ctx.pieces[i].substring(0, index2))
                    } else return false
                }
            } else if (pieces[i] != ctx.pieces[i]) {
                ctx.pathToken.clear()
                return false
            }
        }
        true
    }
}

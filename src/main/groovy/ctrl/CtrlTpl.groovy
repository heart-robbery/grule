package ctrl


import core.Utils
import core.module.ServerTpl
import ratpack.handling.Chain

/**
 * ctrl 层模板类, 所有ctrl 层的类都要继承此类
 */
class CtrlTpl extends ServerTpl {

    def init(Chain chain) {
        Utils.iterateMethod(getClass(), { m ->
            if (m.parameterCount == 1 && m.parameterTypes[0] == Chain.class && 'init' != m.name) {
                m.invoke(this, chain)
            }
        })
        this
    }
}

package ctrl

import core.Utils
import core.module.ServerTpl
import ratpack.handling.Chain

/**
 * ctrl 层模板类, 所有ctrl 层的类都要继承此类
 */
class CtrlTpl extends ServerTpl {
    // 前缀
    protected String prefix


    def init(Chain chain) {
        if (prefix) {
            chain.prefix(prefix) {ch ->
                Utils.iterateMethod(delegate.getClass(), { m ->
                    if (m.parameterCount == 1 && m.parameterTypes[0] == Chain.class && 'init' != m.name) {
                        m.invoke(this, ch)
                    }
                })
            }
        } else {
            Utils.iterateMethod(getClass(), { m ->
                if (m.parameterCount == 1 && m.parameterTypes[0] == Chain.class && 'init' != m.name) {
                    m.invoke(this, chain)
                }
            })
        }
        this
    }
}

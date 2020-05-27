package sevice.rule

import core.module.ServerTpl

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import java.util.function.Supplier

class AttrManager extends ServerTpl {

    final Map<String, String>   attrAlias     = new ConcurrentHashMap<>()
    /**
     * 属性 值函数 映射
     */
    final Map<String, Function<ExecutionContext, Object>> attrGetterMap = new ConcurrentHashMap<>()

    def get(String key, ExecutionContext ctx) {
        def v = attrGetterMap.get(key)?.apply(ctx)
        if (v instanceof Map) {
            v.putAll(v.collectEntries {[attrAlias.get(it.key), it.value] as Object[]})
        }
        v
    }


    AttrManager attrGetter(String key, Supplier getter) {attrGetterMap.put(key, getter); this}
}

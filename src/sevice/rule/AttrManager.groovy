package sevice.rule

import cn.xnatural.enet.event.EL
import core.module.ServerTpl
import core.module.jpa.BaseRepo
import org.hibernate.query.internal.NativeQueryImpl
import org.hibernate.transform.Transformers

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

class AttrManager extends ServerTpl {

    @Lazy def repo = bean(BaseRepo, 'jpa105_repo')
    final Map<String, String>   attrAlias     = new ConcurrentHashMap<>()
    /**
     * 属性 值函数 映射
     */
    final Map<String, Function<ExecutionContext, Object>> attrGetterMap = new ConcurrentHashMap<>()


    @EL(name = 'sys.started', async = true)
    void started() {
        log.info("加载属性")
        repo.trans{se ->
            se.createNativeQuery("select * from rule_field")
                .unwrap(NativeQueryImpl).setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP)
                .list()
        }.each {e ->
            attrAlias.put(e['name'], e['display_name'])
            attrAlias.put(e['display_name'], e['name'])
        }
    }


    def get(String key, ExecutionContext ctx) { attrGetterMap.get(key)?.apply(ctx) }


    /**
     * 得到属性对应的别名
     * @param attr
     * @return
     */
    String alias(String attr) {attrAlias.get(attr)}


    AttrManager attrGetter(String key, Function<ExecutionContext, Object> getter) {attrGetterMap.put(key, getter); this}
}

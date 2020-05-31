package sevice.rule

import cn.xnatural.enet.event.EL
import core.module.ServerTpl
import core.module.jpa.BaseRepo
import org.hibernate.query.internal.NativeQueryImpl
import org.hibernate.transform.Transformers

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.function.Function

class AttrManager extends ServerTpl {

    @Lazy def repo = bean(BaseRepo, 'jpa105_repo')
    final Map<String, String>   attrAlias     = new ConcurrentHashMap<>()
    /**
     * 数据获取函数. 函数名 -> 函数
     */
    protected final Map<String, Consumer<RuleContext>> dataGetterFnMap = new ConcurrentHashMap()
    //同步属性 值获取 函数名 映射
    protected final Map<String, String> attrGet = new ConcurrentHashMap<>()


    @EL(name = 'sys.started', async = true)
    void started() {
        // loadAttrAlias()
    }


    def populateAttr(String key, RuleContext ctx) {
        def fnName = attrGet.get(key)
        if (fnName == null) {
            if (alias(key)) log.warn("属性'" + key + "'没有对应的取值函数")
            return Optional.empty()
        }
        dataGetterFnMap.get(fnName).accept(ctx)
    }


    // 数据获取设置
    def dataFn(String fnName, Consumer<RuleContext> fn) {dataGetterFnMap.put(fnName, fn)}

    // 属性获取配置
    def attrGetConfig(String aName, String fnName, Consumer<RuleContext> fn = null) {
        if (fn) {
            def existFn = dataGetterFnMap.get(fnName)
            if (existFn != fn) {
                throw new Exception("覆盖 函数名 '$fnName' 对应的函数. $aName")
            }
            dataFn(fnName, fn)
        } else if (dataGetterFnMap.get(fnName) == null) {
            throw new Exception("函数名 '$fnName' 没有注册的函数")
        }
        attrGet.put(aName, fnName)
    }


    /**
     * 得到属性对应的别名
     * @param attr
     * @return
     */
    String alias(String attr) {attrAlias.get(attr)}


    /**
     * 注册一个属性同步getter
     * @param key 属性名
     * @param getter 属性值获取函数
     */
    AttrManager attrSyncGetter(String key, Function<RuleContext, Object> getter) {attrSyncGetterMap.put(key, getter); this}

    /**
     * 注册一个属性异步getter
     * @param key 属性名
     * @param getter 属性值获取函数
     */
    AttrManager attrAsyncGetter(String key, Consumer<RuleContext> getter) {attrAsyncGetterMap.put(key, getter); this}


    protected void loadAttrAlias() {
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
}

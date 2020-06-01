package sevice.rule

import cn.xnatural.enet.event.EL
import core.module.ServerTpl
import core.module.jpa.BaseRepo
import org.hibernate.query.internal.NativeQueryImpl
import org.hibernate.transform.Transformers

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

class AttrManager extends ServerTpl {

    @Lazy def repo = bean(BaseRepo, 'jpa105_repo')
    protected final Map<String, String>   attrAlias     = new ConcurrentHashMap<>()
    /**
     * 数据获取函数. 函数名 -> 函数
     */
    protected final Map<String, Consumer<RuleContext>> dataGetterFnMap = new ConcurrentHashMap()
    //同步属性 值获取 函数名 映射
    protected final Map<String, String> attrGet = new ConcurrentHashMap<>()


    @EL(name = 'jpa105.started', async = true)
    protected void init() {
        // loadAttrAlias()
    }


    /**
     * 属性值计算获取
     * @param key 属性名
     * @param ctx 执行上下文
     */
    void populateAttr(String key, RuleContext ctx) {
        log.trace(ctx.logPrefixFn() + "Populate attr: {}", key)
        def fnName = attrGet.get(key)
        if (fnName == null) {
            if (alias(key)) log.warn(ctx.logPrefixFn() + "属性'" + key + "'没有对应的取值函数")
            ctx.setProperty(key, null)
            return
        }
        def fn = dataGetterFnMap.get(fnName)
        if (fn) {
            log.trace(ctx.logPrefixFn() + "Get attr '{}' value apply function: '{}'", key, fnName)
            fn.accept(ctx)
        }
        else {
            log.debug(ctx.logPrefixFn() + "Not fund attr '{}' mapped getter function", key)
            ctx.setProperty(key, null)
        }
    }


    // 数据获取设置
    def dataFn(String fnName, Consumer<RuleContext> fn) {dataGetterFnMap.put(fnName, fn)}


    // 属性获取配置
    def attrGetConfig(String aName, String fnName, Consumer<RuleContext> fn = null) {
        if (fn) {
            def existFn = dataGetterFnMap.get(fnName)
            if (existFn && existFn != fn) {
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

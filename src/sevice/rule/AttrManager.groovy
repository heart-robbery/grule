package sevice.rule

import cn.xnatural.enet.event.EL
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import core.module.OkHttpSrv
import core.module.ServerTpl
import core.module.jpa.BaseRepo
import org.hibernate.query.internal.NativeQueryImpl
import org.hibernate.transform.Transformers

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

class AttrManager extends ServerTpl {

    @Lazy def                                                  repo            = bean(BaseRepo, 'jpa105_repo')
    protected final Map<String, String>                        attrAlias       = new ConcurrentHashMap<>()
    protected final Map<String, Class>                        attrType       = new ConcurrentHashMap<>()
    /**
     * 数据获取函数. 函数名 -> 函数
     */
    protected final Map<String, Function<RuleContext, Object>> dataGetterFnMap = new ConcurrentHashMap()
    //同步属性 值获取 函数名 映射
    protected final Map<String, String>                        attrFnMap       = new ConcurrentHashMap<>()


    @EL(name = 'jpa105.started', async = true)
    protected void init() {
        // 有顺序
        loadAttrAlias()
        loadDataCollector()
    }


    /**
     * 获取属性值
     * @param key
     * @param ctx
     * @return
     */
    def getAttr(String key, RuleContext ctx) {
        def fnName = attrFnMap.get(key)
        if (fnName == null) {
            if (alias(key)) log.warn(ctx.logPrefixFn() + "属性'" + key + "'没有对应的取值函数")
            return null
        }
        def fn = dataGetterFnMap.get(fnName)
        if (fn) {
            log.trace(ctx.logPrefixFn() + "Get attr '{}' value apply function: '{}'", key, fnName)
            return fn.apply(ctx)
        }
        else {
            log.debug(ctx.logPrefixFn() + "Not fund attr '{}' mapped getter function", key)
            return null
        }
    }


    // 数据获取设置
    def dataFn(String fnName, Function<RuleContext, Object> fn) {dataGetterFnMap.put(fnName, fn)}


    // 属性获取配置
    def attrGetConfig(String aName, String fnName, Function<RuleContext, Object> fn = null) {
        if (fn) {
            def existFn = dataGetterFnMap.get(fnName)
            if (existFn && existFn != fn) {
                throw new Exception("覆盖 函数名 '$fnName' 对应的函数. $aName")
            }
            dataFn(fnName, fn)
        } else if (dataGetterFnMap.get(fnName) == null) {
            throw new Exception("函数名 '$fnName' 没有注册的函数")
        }
        attrFnMap.put(aName, fnName)
    }


    /**
     * 得到属性对应的别名
     * @param attr
     * @return
     */
    String alias(String attr) {attrAlias.get(attr)}

    /**
     * 获取属性值类型
     * @param attr
     * @return
     */
    protected Class type(String attr) {attrType.get(attr)}


    /**
     * 类型转换
     * @param key
     * @param value
     * @return
     */
    Object convert(String key, Object value) {
        if (value == null) return value
        def t = type(key)
        if (String == t) value = value.toString()
        else if (Integer == t) value = Integer.valueOf(value)
        else if (BigDecimal == t) value = new BigDecimal(value.toString())
        else if (Boolean == t) value = Boolean.valueOf(value)
        return value
    }


    protected void loadAttrAlias() {
        log.info("加载属性别名配置")
        repo.trans{se ->
            se.createNativeQuery("select * from rule_field")
                    .unwrap(NativeQueryImpl).setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP)
                    .list()
        }.each {e ->
            attrAlias.put(e['name'], e['display_name'])
            attrAlias.put(e['display_name'], e['name'])

            Class t = String
            if (e['type'] == 'INT') t = Integer
            else if (e['type'] == 'BOOLEAN') t = Boolean
            else if (e['type'] == 'DOUBLE') t = BigDecimal
            // TODO 其它类型
            attrType.put(e['name'], t)
            attrType.put(e['display_name'], t)
        }
    }

    protected void loadDataCollector() {
        log.info("加载数据集成配置")
        repo.trans{se ->
            se.createNativeQuery("select * from data_collector")
                .unwrap(NativeQueryImpl).setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP)
                .list()
        }.each {e ->
            Map<String, String> outAttrMap = JSON.parseArray(e['output_config']).collectEntries {JSONObject jo ->
                [jo['responseField'], jo['systemField']]
            }
            Map<String, String> inAttrMap = JSON.parseArray(e['input_config']).collectEntries {JSONObject jo ->
                [jo['requestField'], jo['systemField']]
            }

            dataFn(e['name']) {ctx ->
                try {
                    def r = bean(OkHttpSrv).post(e['url'])
                        .jsonBody({
                            def inValueMap = inAttrMap.collectEntries {['${' + it.key + '}', ctx.getAttr(it.value)]}
                            def inTplJo = JSON.parseObject(e['input_template'])
                            for (def itt = inTplJo.iterator(); itt.hasNext(); ) {
                                def ee = itt.next()
                                ee.value = inValueMap.get(ee.value)
                                if (ee.value == null) itt.remove()
                            }
                            inTplJo.toString()
                        }())
                        .debug().execute()
                    def jo = JSON.parseObject(r)
                    if ('0000' == jo['code']) {
                        JSONObject dataJo = jo.containsKey('data') ? jo['data'] : jo['result']
                        return dataJo.collectEntries {ee ->
                            [outAttrMap[(ee.key)], ee.value]
                        }
                    }
                    return [errorCode: jo['code'], data_collector_cn: e['display_name']]
                } catch (Exception ex) {
                    log.error("数据获取函数 " +e['name']+ "执行错误")
                }
                null
            }

            // 属性取值函数配置
            outAttrMap.forEach((n1, n2) -> {
                if ('errorCode' != n2) {
                    attrGetConfig(n2, e['name'])
                    String n = attrAlias.get(n2)
                    if (n && n != n2) attrGetConfig(n, e['name'])
                }
            })
        }
    }
}

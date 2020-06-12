package service.rule

import cn.xnatural.enet.event.EL
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONException
import com.alibaba.fastjson.JSONObject
import core.module.OkHttpSrv
import core.module.ServerTpl
import core.module.jpa.BaseRepo
import org.hibernate.query.internal.NativeQueryImpl
import org.hibernate.transform.Transformers

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

/**
 * 属性管理
 * 属性别名
 * 属性值函数
 */
class AttrManager extends ServerTpl {

    @Lazy def                                                      repo            = bean(BaseRepo, 'jpa_kratos_repo')
    protected final Map<String, String>                            attrAlias       = new ConcurrentHashMap<>()
    protected final Map<String, Class>                             attrType        = new ConcurrentHashMap<>()
    /**
     * 数据获取函数. 函数名 -> 函数
     */
    protected final Map<String, Function<DecisionContext, Object>> dataGetterFnMap = new ConcurrentHashMap()
    //属性 函数名
    protected final Map<String, String>                            attrFnMap       = new ConcurrentHashMap<>()
    @Lazy Set<String> ignoreGetAttr = new HashSet<>(
        (getStr('ignoreGetAttr', '')).split(",").collect {it.trim()}.findAll{it}
    )


    @EL(name = 'jpa_kratos.started', async = true)
    void init() {
        // 有顺序
        loadAttrAlias()
        loadDataCollector()
        ep.fire("${name}.started")
    }


    /**
     * 获取属性值
     * @param key
     * @param ctx
     * @return
     */
    def getAttr(String key, DecisionContext ctx) {
        if (ignoreGetAttr.contains(key)) return null
        def fnName = attrFnMap.get(key)
        if (fnName == null) {
            if (alias(key)) log.warn(ctx.logPrefix() + "属性'" + key + "'没有对应的取值函数")
            return null
        }
        def fn = dataGetterFnMap.get(fnName)
        if (fn) {
            if (ctx.appliedGetterFn.contains(fnName)) return null
            ctx.appliedGetterFn.add(fnName)
            log.debug(ctx.logPrefix() + "Get attr '{}' value apply function: '{}'", key, fnName)
            return fn.apply(ctx)
        }
        else {
            log.debug(ctx.logPrefix() + "Not fund attr '{}' mapped getter function '{}'", key, fnName)
            return null
        }
    }


    /**
     * 决策产生的数据接口调用
     * @param ctx 当前决策 DecisionContext
     * @param dataCfg 数据配置
     * @param jsonBody 入参json
     * @param resultStr 接口返回结果
     * @param resolveResult 解析结果
     * @param spend 接口调用花费时间
     */
    @EL(name = 'decision.dataCollect')
    void dataCollected(DecisionContext ctx, Map dataCfg, String jsonBody, String resultStr, Map resolveResult, Long spend) {
        log.info(ctx.logPrefix() + "接口调用: " + dataCfg['display_name'] + ", spend: " + spend + "ms, params: " + jsonBody + ", returnStr: " + resultStr + ", resolveResult: " + resolveResult)
    }


    /**
     * 数据获取函数
     * @param fnName 函数名
     * @param fn 函数
     */
    def dataFn(String fnName, Function<DecisionContext, Object> fn) { dataGetterFnMap.put(fnName, fn) }


    /**
     * 属性获取配置
     * @param aName 属性名
     * @param fnName 获取函数名
     * @param fn 函数
     */
    def attrGetConfig(String aName, String fnName, Function<DecisionContext, Object> fn = null) {
        if (fn) {
            def existFn = dataGetterFnMap.get(fnName)
            if (existFn && existFn != fn) {
                log.warn("覆盖 函数名 '{}' 对应的函数. {}", fnName, aName)
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
    String alias(String attr) {def r = attrAlias.get(attr); (r == null ? attr : r)}

    /**
     * 获取属性值类型
     * @param attr
     * @return
     */
    protected Class type(String attr) {attrType.get(attr)}


    /**
     * 属性值类型转换
     * @param key 属性名
     * @param value 值
     * @return 转换后的值
     */
    Object convert(String key, Object value) {
        if (value == null) return value
        def t = type(key)
        if (String == t) value = value.toString()
        else if (Long == t) value = Long.valueOf(value)
        else if (BigDecimal == t) value = new BigDecimal(value.toString())
        else if (Boolean == t) value = Boolean.valueOf(value)
        return value
    }


    /**
     * 加载属性
     */
    protected void loadAttrAlias() {
        log.info("加载属性配置")
        repo.trans{se ->
            se.createNativeQuery("select * from rule_field")
                    .unwrap(NativeQueryImpl).setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP)
                    .list()
        }.each {e ->
            attrAlias.put(e['name'], e['display_name'])
            attrAlias.put(e['display_name'], e['name'])

            Class t = String
            if (e['type'] == 'INT') t = Long
            else if (e['type'] == 'BOOLEAN') t = Boolean
            else if (e['type'] == 'DOUBLE') t = BigDecimal // 默认高精度计算
            // TODO 其它类型
            attrType.put(e['name'], t)
            attrType.put(e['display_name'], t)

            // 特殊属性值配置
            if ('isOrNotWeekend' == e['name']) { // DYN_申请日期是否周末
                attrGetConfig(e['name'], 'getter_isOrNotWeekend') {ctx ->
                    (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) - 1) >= 6 ? true : false
                }
                attrGetConfig(e['display_name'], 'getter_isOrNotWeekend')
            }

//            if ("idExpiryDateToAppDayNum" == e['name']) { // 身份证到期日距查询当日天数
//                attrGetConfig(e['name'], 'attr_getter_idExpiryDateToAppDayNum') {ctx ->
//                    String id = ctx.getAttr("idNumber")
//                }
//                attrGetConfig(e['display_name'], 'attr_getter_idExpiryDateToAppDayNum')
//            }
        }
    }


    /**
     * 加载数据集成
     */
    protected void loadDataCollector() {
        log.info("加载数据集成配置")
        def http = bean(OkHttpSrv)
        repo.trans{se ->
            se.createNativeQuery("select * from data_collector")
                .unwrap(NativeQueryImpl).setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP)
                .list()
        }.each {e ->
            // 忽略接口
            if ('_modelPlatform' == e['name']) return

            Map<String, String> outAttrMap = JSON.parseArray(e['output_config']).collectEntries {JSONObject jo ->
                [jo['responseField'], jo['systemField']]
            }
            Map<String, String> inAttrMap = JSON.parseArray(e['input_config']).collectEntries {JSONObject jo ->
                [jo['requestField'], jo['systemField']]
            }

            dataFn(e['name']) {ctx -> // 3方接口数据获取
                // 请求发送的json body
                String jsonBody
                // 接口返回的字符串
                String retStr
                // 最终要返回的值(解析后的键值对)
                def ret
                // 接口请求共计花费时间
                Long spend
                try {
                    // 解析数据集成里面的输入配置
                    def inValueMap = inAttrMap.collectEntries {['${' + it.key + '}', ctx.getAttr(it.value)]}
                    def inTplJo = JSON.parseObject(e['input_template'])
                    for (def itt = inTplJo.iterator(); itt.hasNext(); ) {
                        def ee = itt.next()
                        ee.value = inValueMap.get(ee.value)
                        if (ee.value == null) itt.remove()
                    }
                    jsonBody = inTplJo.toString() // 请求body json字符串

                    long start = System.currentTimeMillis()
                    // 接口请求 超时默认重试3次
                    for (int i = 0, size = getInteger("retry", 3); i < size; i++) {
                        try {
                            retStr = http.post(e['url']).jsonBody(jsonBody).execute() // 发送http
                            if (retStr == null || retStr.empty) {
                                ret = [errorCode: 'TD501']
                                throw new Exception("接口${e['name']}无返回结果")
                            }
                            break
                        } catch (Exception ex) {
                            if (ex instanceof SocketTimeoutException || ex instanceof ConnectException) { // 超时则重试
                                ret = [errorCode: 'TD500']
                                continue
                            }
                            ret = [errorCode: 'TD504']
                            throw ex
                        } finally {
                            spend = System.currentTimeMillis() - start
                        }
                    }

                    // 处理接口返回的结果
                    try {
                        def rJo = JSON.parseObject(retStr)
                        if ('0000' == rJo['code']) { // 取清洗结果
                            JSONObject dataJo = rJo.containsKey('data') ? rJo['data'] : rJo['result']
                            ret = dataJo.collectEntries { [outAttrMap[(it.key)], it.value] }
                        } else {
                            ret = [errorCode: rJo['code']]
                        }
                    } catch (Exception ex) {
                        ret = [errorCode: 'TD503']
                        if (ex instanceof JSONException) throw new Exception("接口${e['name']}返回的不是Json. " + retStr, ex)
                        else throw ex
                    }
                } catch (Exception ex) {
                    log.error(ctx.logPrefix() + "数据获取函数 " + e['name'] + " 发生错误: " + ex.message, ex)
                } finally {
                    ep.fire("decision.dataCollect", ctx, e, jsonBody, retStr, ret, spend)
                }
                return ret
            }

            // 属性取值函数配置
            outAttrMap.forEach((n1, n2) -> {
                if ('errorCode' != n2) {
                    attrGetConfig(n2, e['name'])
                    String n = alias(n2)
                    if (n && n != n2) attrGetConfig(n, e['name'])
                }
            })
        }
    }
}

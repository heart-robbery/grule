package service.rule

import cn.xnatural.enet.event.EL
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONException
import com.alibaba.fastjson.JSONObject
import core.OkHttpSrv
import core.ServerTpl
import core.Utils
import core.jpa.BaseRepo
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
            log.warn(ctx.logPrefix() + "属性'" + key + "'没有对应的取值函数")
            return null
        }

        // 函数执行
        def doApply = {Function<DecisionContext, Object> fn ->
            log.debug(ctx.logPrefix() + "Get attr '{}' value apply function: '{}'", key, fnName)
            fn.apply(ctx)
        }

        def fn = dataGetterFnMap.get(fnName)
        if (fn) {
            return doApply(fn)
        } else {
            dataCollect( // 重新去数据库中查找
                repo.trans{se ->
                    se.createNativeQuery("select * from data_collector where name=:n")
                        .setParameter('n', fnName)
                        .unwrap(NativeQueryImpl).setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP)
                        .setMaxResults(1).uniqueResult()
                }
            )
            fn = dataGetterFnMap.get(fnName)
            if (fn) return doApply(fn)
            else {
                log.debug(ctx.logPrefix() + "Not fund attr '{}' mapped getter function '{}'", key, fnName)
                return null
            }
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
        log.info(ctx.logPrefix() + "接口调用: " + dataCfg['name'] + ", spend: " + spend + "ms, params: " + jsonBody + ", returnStr: " + resultStr + ", resolveResult: " + resolveResult)
    }


    /**
     * 数据获取函数
     * @param fnName 函数名
     * @param fn 函数
     */
    Function<DecisionContext, Object> dataFn(String fnName, Function<DecisionContext, Object> fn) { dataGetterFnMap.put(fnName, fn) }


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
     * @return null: 没有别名
     */
    String alias(String attr) { attrAlias.get(attr) }


    /**
     * 属性值类型转换
     * @param key 属性名
     * @param value 值
     * @return 转换后的值
     */
    Object convert(String key, Object value) {
        if (value == null) return value
        Utils.to(value, attrType.get(key))
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

            // =================特殊属性值函数配置=====================
            if ('isOrNotWeekend' == e['name']) { // DYN_申请日期是否周末
                attrGetConfig(e['name'], 'getter_isOrNotWeekend') {ctx ->
                    (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) - 1) >= 6 ? true : false
                }
                attrGetConfig(e['display_name'], 'getter_isOrNotWeekend')
            }

            if ('sex' == e['name']) { // 性别判断
                attrGetConfig(e['name'], 'getter_sex') {ctx ->
                    String idNum = ctx.getAttr('idNumber')
                    if (idNum.length() >= 17) {
                        Integer.parseInt(idNum.substring(16, 17)) % 2 == 0 ? 'F' : 'M'
                    } else null
                }
                attrGetConfig(e['display_name'], 'getter_sex')
            }
        }
    }


    /**
     * 加载数据集成
     */
    protected void loadDataCollector() {
        log.info("加载数据集成配置")
        repo.trans{se ->
            se.createNativeQuery("select * from data_collector")
                .unwrap(NativeQueryImpl).setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP)
                .list()
        }.each {dataCollect(it)}
    }


    /**
     * 数据集成中的每个接口设置获取函数
     * @param record
     */
    protected void dataCollect(Map record) {
        if (record == null) return
        // 忽略接口
        if ('_modelPlatform' == record['name']) return

        Map<String, String> outAttrMap = JSON.parseArray(record['output_config']).collectEntries {JSONObject jo ->
            [jo['responseField'], jo['systemField']]
        }
        Map<String, String> inAttrMap = JSON.parseArray(record['input_config']).collectEntries {JSONObject jo ->
            [jo['requestField'], jo['systemField']]
        }
        def http = bean(OkHttpSrv)
        dataFn(record['name']) {ctx -> // 3方接口数据获取
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
                def inTplJo = JSON.parseObject(record['input_template'])
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
                        ret = null
                        retStr = http.post(record['url']).jsonBody(jsonBody).execute() // 发送http
                        if (retStr == null || retStr.empty) {
                            ret = [errorCode: 'TD501']
                            throw new Exception("接口${record['name']}无返回结果")
                        }
                        break
                    } catch (ex) {
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
                } catch (ex) {
                    ret = [errorCode: 'TD503']
                    if (ex instanceof JSONException) throw new Exception("接口${record['name']}返回的不是Json. " + retStr, ex)
                    else throw ex
                }
            } catch (ex) {
                log.error(ctx.logPrefix() + "数据获取函数 " + record['name'] + " 发生错误: " + ex.message, ex)
            } finally {
                ep.fire("decision.dataCollect", ctx, record, jsonBody, retStr, ret, spend)
            }
            return ret
        }

        // 属性取值函数配置
        outAttrMap.forEach((n1, n2) -> {
            if ('errorCode' != n2) {
                attrGetConfig(n2, record['name'])
                String n = alias(n2)
                if (n && n != n2) attrGetConfig(n, record['name'])
            }
        })
    }
}

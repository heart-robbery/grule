package service.rule

import cn.xnatural.enet.event.EC
import cn.xnatural.enet.event.EL
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import core.Remoter
import core.ServerTpl
import core.jpa.BaseRepo
import dao.entity.Decision
import dao.entity.FieldType
import service.rule.spec.DecisionSpec

import java.text.SimpleDateFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function

/**
 * 决策管理器
 */
class DecisionManager extends ServerTpl {
    @Lazy def repo = bean(BaseRepo, 'jpa_rule_repo')

    protected final Map<String, DecisionHolder> decisionMap = new ConcurrentHashMap<>()


    @EL(name = 'jpa_rule.started', async = true)
    void start() {
        load()
    }


    /**
     * 查找 决策
     * @param decisionId
     * @return
     */
    DecisionHolder findDecision(String decisionId) {
        def holder = decisionMap.get(decisionId)
        if (holder == null) {
            addDecision(repo.find(Decision) { root, query, cb -> cb.equal(root.get('decisionId'), decisionId)})
            holder = decisionMap.get(decisionId)
        }
        return holder
    }


    @EL(name = ['decisionChange', 'decision.dataVersion'], async = true)
    void listenDecisionChange(EC ec, String decisionId) {
        if (!decisionId) return
        def decision = repo.find(Decision) { root, query, cb -> cb.equal(root.get('decisionId'), decisionId)}
        if (decision == null) {
            decisionMap.remove(decisionId)
            log.info("delDecision: " + decisionId)
        } else {
            addDecision(decision)
            log.info("decisionChange: " + decisionId)
        }
        def remoter = bean(Remoter)
        if (remoter && ec?.source() != remoter) { // 不是远程触发的事件
            remoter.dataVersion('decision').update(decisionId, decision ? decision.updateTime.time : System.currentTimeMillis())
        }
    }



    protected void load() {
        log.info("加载决策")
        Set<String> ids = (decisionMap ? new HashSet<>() : null)
        def threshold = new AtomicInteger(1)
        def tryCompleteFn = {
            if (threshold.decrementAndGet() > 0) return
            if (ids) {
                decisionMap.collect {it.key}.each { decisionId ->
                    if (!ids.contains(decisionId)) { // 删除库里面没有, 内存里面有的数据
                        decisionMap.remove(decisionId)
                    }
                }
            }
        }
        for (int page = 0, limit = 50; ; page++) {
            def ls = repo.findList(Decision, page * limit, limit)
            if (!ls) { // 结束
                tryCompleteFn()
                break
            }
            threshold.incrementAndGet()
            async { // 异步加载
                ls.each { decision ->
                    ids?.add(decision.decisionId)
                    try {
                        addDecision(decision)
                    } catch (ex) {
                        log.error("加载决策'${decision.name}:${decision.decisionId}'错误", ex)
                    }
                }
                tryCompleteFn()
            }
        }
    }


    /**
     * 添加决策
     * @param decision
     */
    protected void addDecision(Decision decision) {
        if (!decision || !decision.dsl) return
        def spec = DecisionSpec.of(decision.dsl)
        def apiConfig = decision.apiConfig ? JSON.parseArray(decision.apiConfig) : null
        decisionMap.put(spec.决策id, new DecisionHolder(decision: decision, spec: spec, paramValidator: (apiConfig ? { Map<String, Object> params ->
            apiConfig.each { JSONObject paramCfg ->
                def code = paramCfg.getString('code') // 参数code
                def value = params.get(code)
                if (paramCfg.getBoolean('require') && value == null) { // 必传验证
                    throw new IllegalArgumentException("Param '$code' require")
                }
                def fixValue // 固定值
                def enumValues // 枚举值
                def type = FieldType.valueOf(paramCfg.getString('type')) // 参数类型. FieldType
                if (type == null || !FieldType.enumConstants.find {it == type}) throw new Exception("参数验证类型配置错误. type: $type")

                if (FieldType.Bool == type) { // Boolean 值验证
                    if (value == null) value = paramCfg.getBoolean('defaultValue')
                    if (value != null) {
                        if (!'true'.equalsIgnoreCase(value) && !'false'.equalsIgnoreCase(value)) {
                            throw new IllegalArgumentException("Param '$code' type is boolean. value is 'true' or 'false'")
                        }
                        value = value instanceof Boolean ? value : Boolean.valueOf(value)
                    }
                    fixValue = paramCfg.getBoolean('fixValue')
                }
                if (FieldType.Int == type) { // 整数验证
                    if (value == null) value = paramCfg.getLong('defaultValue')
                    if (value != null) {
                        try {
                            value = value instanceof Long ? value : Long.valueOf(value)
                        } catch (ex) {
                            throw new IllegalArgumentException("Param '$code' is not a number. value: $value", ex)
                        }
                        def min = paramCfg.getLong('min')
                        if (min != null && value < min) {
                            throw new IllegalArgumentException("Param '$code' must >= $min")
                        }
                        def max = paramCfg.getLong('max')
                        if (max != null && value > max) {
                            throw new IllegalArgumentException("Param '$code' must <= $max")
                        }
                    }
                    fixValue = paramCfg.getLong('fixValue')
                    enumValues = paramCfg.getJSONArray('enumValues')?.collect {JSONObject jo -> jo?.getLong('value')}?.findAll{it}
                }
                if (FieldType.Decimal == type) { // 小数验证
                    if (value == null) value = paramCfg.getBigDecimal('defaultValue')
                    if (value != null) {
                        try {
                            value = value instanceof BigDecimal ? value : new BigDecimal(value.toString())
                        } catch (ex) {
                            throw new IllegalArgumentException("Param '$code' is not a decimal. value: $value", ex)
                        }
                        def min = paramCfg.getBigDecimal('min')
                        if (min != null && value < min) {
                            throw new IllegalArgumentException("Param '$code' must >= $min")
                        }
                        def max = paramCfg.getBigDecimal('max')
                        if (max != null && value > max) {
                            throw new IllegalArgumentException("Param '$code' must <= $max")
                        }
                    }
                    fixValue = paramCfg.getBigDecimal('fixValue')
                    enumValues = paramCfg.getJSONArray('enumValues')?.collect {JSONObject jo -> jo?.getBigDecimal('value')}?.findAll{it}
                }
                if (FieldType.Str == type) { // 字符串验证
                    if (value == null) value = paramCfg.getString('defaultValue')
                    if (value != null) {
                        def maxLength = paramCfg.getInteger('maxLength')
                        if (maxLength != null && value.toString().length() > maxLength) {
                            throw new IllegalArgumentException("Param '$code' length must < $maxLength")
                        }
                        def fixLength = paramCfg.getInteger('fixLength')
                        if (fixLength != null && value.toString().length() != fixLength) {
                            throw new IllegalArgumentException("Param '$code' length must equal $fixLength")
                        }
                    }
                    fixValue = paramCfg.getString('fixValue')
                    enumValues = paramCfg.getJSONArray('enumValues')?.collect {JSONObject jo -> jo?.getString('value')}?.findAll{it}
                }
                if (FieldType.Time == type) { // 时间验证
                    if (value == null) value = paramCfg.getString('defaultValue')
                    if (value != null) {
                        String format = paramCfg.getString('format')
                        if (!format) throw new IllegalArgumentException("Param '$code' type is Time, format must config")
                        try {
                            // if (value.toString().length() != format.length()) throw new Exception()
                            value = new SimpleDateFormat(format).parse(value.toString())
                        } catch (ex) {
                            throw new IllegalArgumentException("Param '$code' format error. format: $format, value: $value")
                        }
                    }
//                    def min = paramCfg.getDate('min')
//                    if (min != null && value < min) {
//                        throw new IllegalArgumentException("Param '$code' must >= ${paramCfg.getString('min')}")
//                    }
//                    def max = paramCfg.getDate('max')
//                    if (max != null && value > max) {
//                        throw new IllegalArgumentException("Param '$code' must <= ${paramCfg.getString('max')}")
//                    }
//                    fixValue = paramCfg.getDate('fixValue')
//                    enumValues = paramCfg.getJSONArray('enumValues')?.collect {JSONObject jo -> jo?.getDate('value')}?.findAll{it}
                }

                if (value) {
                    if (fixValue != null) {  // 固定值验证
                        if (value != fixValue) {
                            throw new IllegalArgumentException("Param '$code' value fixed be '$fixValue'")
                        }
                    }
                    else if (enumValues) { // 枚举值验证
                        if (!enumValues.find {it == value}) {
                            throw new IllegalArgumentException("Param '$code' enum values: '${enumValues.join(",")}'")
                        }
                    }
                }
                params.put(code, value) // 类型矫正
                true
            }
        } : null)))
        log.debug("添加决策: {}:{}", spec.决策名, spec.决策id)
    }


    /**
     * 决策 Holder
     */
    class DecisionHolder {
        // 对应实体 decision
        Decision                               decision
        // dsl spec
        DecisionSpec                           spec
        // 参数验证函数
        Function<Map<String, Object>, Boolean> paramValidator
    }
}

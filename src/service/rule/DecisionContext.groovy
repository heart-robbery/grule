package service.rule

import cn.xnatural.enet.event.EP
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import service.rule.spec.DecisionSpec
import service.rule.spec.PolicySpec
import service.rule.spec.RuleSpec

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 决策执行上下文
 */
class DecisionContext {
    protected static final Logger    log     = LoggerFactory.getLogger(DecisionContext)
    // 执行上下文id
                    String           id
    // 开始时间
    final           Date             startup = new Date()
                    DecisionSpec     decisionSpec
    protected       RuleSpec         curRuleSpec
    // 当前正在执行的规则
    protected       PassedRule       curPassedRule
    protected       PolicySpec       curPolicySpec
    // 执行过的规则记录
    protected final List<PassedRule> rules   = new LinkedList<>()
    // 最终决策结果
                    Decision         finalDecision
                    AttrManager      attrManager
                    EP               ep
    // 运行状态. TODO 以后做暂停时 running == false
    protected final def                              running           = new AtomicBoolean(false)
    // 是否已启动
    protected final def                              started           = new AtomicBoolean(false)
    // 是否执行结束
    protected final def                              end               = new AtomicBoolean(false)
    // 输入参数
    Map<String, Object>                                              input
    // 最终数据属性值
    @Lazy protected Map<String, Object>              data              = new Data(this)
    // 数据收集器名 -> 数据收集结果集
    @Lazy protected Map<String, Map<String, Object>> dataCollectResult = new ConcurrentHashMap<>()
    // 规则执行迭代器
    @Lazy protected def                              ruleIterator      = new RuleIterator(this)
    // 执行结果 异常
    protected Exception exception
    // 执行状态
    protected String status = '0000'


    /**
     * 开始执行流程
     */
    void start() {
        if (!started.compareAndSet(false, true)) return
        log.info(logPrefix() + "开始")
        input?.forEach {k, v -> setAttr(k, v) }
        if (!ruleIterator.hasNext()) {
            finalDecision = Decision.Reject
            exception = "没有可执行的策略"
            log.warn(logPrefix() + exception)
        }
        trigger()
    }


    /**
     * 触发执行流程
     */
    protected final void trigger() {
        if (end.get()) return
        if (!running.compareAndSet(false, true)) return
        try {
            while (running.get() && ruleIterator.hasNext()) {
                def r = ruleIterator.next()
                if (!r) break
                if (!r.enabled) continue
                def decision = decide(r)
                if (finalDecision == Decision.Review && decision == Decision.Accept) {
                    // 保留decision并继续往下执行
                } else finalDecision = decision
                if (Decision.Reject == finalDecision) break
            }
            if (Decision.Reject == finalDecision || !ruleIterator.hasNext()) {
                end.set(true); finalDecision = finalDecision?:Decision.Accept
                running.set(false); curPolicySpec = null; curPassedRule = null; curRuleSpec = null
                log.info(logPrefix() + "结束成功. 共执行: " + (System.currentTimeMillis() - startup.time) + "ms "  + result())
                ep?.fire("decision.end", this)
            }
        } catch (ex) {
            end.set(true); finalDecision = Decision.Reject
            running.set(false); curPolicySpec = null; curPassedRule = null; curRuleSpec = null
            status = 'EEEE'; this.exception = ex
            log.error(logPrefix() + "结束错误. 共执行: " + (System.currentTimeMillis() - startup.getTime()) + "ms " + result(), ex)
            ep?.fire("decision.end", this)
        }
    }


    /**
     * 规则决策
     * @param RuleSpec 规则描述
     * @return Decision 决策结果
     */
    protected Decision decide(RuleSpec r) {
        if (!r.enabled) return null
        curRuleSpec = r
        curPassedRule = new PassedRule(attrs: [*:r.attrs, 规则名: r.规则名]); rules.add(curPassedRule)
        log.trace(logPrefix() + "开始执行规则")

        Decision decision
        try {
            r.decisionFn?.forEach(e -> {
                def d = e.v2(this.data)
                if ("Reject" == e.v1) {
                    decision = d
                    log.trace(logPrefix() + "拒绝函数执行结果: " + d)
                } else if ("Accept" == e.v1) {
                    decision = d
                    log.trace(logPrefix() + "通过函数执行结果: " + d)
                } else if ("Review" == e.v1) {
                    decision = d
                    log.trace(logPrefix() + "人工审核函数执行结果: " + d)
                } else if ("Operate" == e.v1) {
                    log.trace(logPrefix() + "操作函数执行完成")
                } else {
                    log.trace(logPrefix() + e.v1 + "函数执行完成. " + d)
                }
            })
            log.debug(logPrefix() + "结束执行规则. decision: " + decision  + ", attrs: " + curPassedRule.data)
        } catch (ex) {
            decision = Decision.Reject
            log.error(logPrefix() + "规则执行错误. " + decision + ", attrs: " + curPassedRule.data)
            throw ex
        } finally {
            curPassedRule.setDecision(decision); curRuleSpec = null; curPassedRule = null
        }
        return decision
    }


    /**
     * 获取属性
     * @param aName 属性名
     * @return 属性值
     */
    Object getAttr(String aName) {
        boolean f = curPassedRule?.data?.containsKey(aName)
        data.get(aName)
        if (!f) curPassedRule?.data?.remove(aName)
    }


    /**
     * 设置属性
     * @param aName 属性名
     * @param value 属性值
     */
    void setAttr(String aName, Object value) { data.put(aName, value) }


    /**
     * 设置为规则用到的属性
     * @param key
     * @param value
     */
    protected void ruleAttr(String key, Object value) {
        if (curPassedRule && !end.get()) curPassedRule.data.put(key, value)
    }


    /**
     * 规则执行迭代器
     */
    protected class RuleIterator implements Iterator<RuleSpec> {
        final DecisionContext ctx

        RuleIterator(DecisionContext ctx) {this.ctx = ctx}

        // 策略迭代器
        Iterator<PolicySpec> policyItt = ctx.getDecisionSpec().policies.iterator()
        // 规则迭代器
        Iterator<RuleSpec>   itt

        @Override
        boolean hasNext() { (itt && itt.hasNext()) || policyItt.hasNext() }

        @Override
        RuleSpec next() {
            if ((ctx.curPolicySpec == null || (itt == null || !itt.hasNext())) && policyItt.hasNext()) {
                ctx.curPolicySpec = policyItt.next()
                if (ctx.curPolicySpec.condition) {
                    if (!ctx.curPolicySpec.condition(ctx.data)) {
                        ctx.curPolicySpec = null
                        return next()
                    }
                }
                log.debug(logPrefix() + "开始执行策略")
            }

            if (itt == null || !itt.hasNext()) itt = ctx.curPolicySpec?.rules?.iterator()
            if (itt && itt.hasNext()) return itt.next()
            return null
        }
    }


    /**
     * 数据存放. 用于规则闭包执行上下文
     */
    protected class Data extends LinkedHashMap<String, Object> {
        final DecisionContext ctx

        Data(DecisionContext ctx) {this.ctx = ctx}

        @Override
        Object get(Object aName) {
            if (aName == null) return null
            def value = super.get(aName)
            if (value == null && !super.containsKey(aName) && !ctx.end.get()) {// 属性值未找到,则从属性管理器获取
                safeSet(aName.toString(), null) // 代表属性已从外部获取过,后面就不再去获取了(防止重复获取)
                safeSet((String) aName, ctx.getAttrManager().dataCollect(aName.toString(), ctx))
                value = super.get(aName)
            }
            if (value instanceof Optional) {
                if (value.present) value = value.get()
                else value = null
            }
            ctx.ruleAttr(aName.toString(), value)
            return value
        }


        /**
         * 设置属性
         * @param key
         * @param value
         */
        protected Object safeSet(String key, Object value) {
            if (value instanceof Optional) {
                value = value.present ? Optional.ofNullable(ctx.getAttrManager().convert(key, value.get())) : value
            } else {
                value = ctx.getAttrManager().convert(key, value) // 属性值类型转换
            }
            super.put(key, value)

            def n = ctx.getAttrManager().alias(key)
            if (n && n != key) super.put(n, value)
            value
        }


        @Override
        Object put(String key, Object value) {
            ctx.ruleAttr(key, safeSet(key, value))
            value
        }
    }


    /**
     * 执行过的规则
     */
    protected class PassedRule {
        // 规则 最终决策
        Decision  decision
        // 规则属性
        Map<String, Object> attrs
        // 执行规则过程中产生的或用到的数据集
        final Map<String, Object> data = new LinkedHashMap()

        @Override
        String toString() {
            return [attrs: attrs, decision: decision, data: data].toMapString()
        }
    }


    // 日志前缀
    protected String logPrefix() {
        "[${id? "$id, " :''}${decisionSpec.决策id? "决策: $decisionSpec.决策id" :''}${curPolicySpec? ", 策略: ${curPolicySpec.策略名}" :''}${curRuleSpec ? ", 规则: ${curRuleSpec.规则名}" :''}] -> "
    }


    /**
     * 整条决策 所有信息
     * @return
     */
    private Map<String, Object> summary
    Map<String, Object> summary() {
        if (this.summary && end.get()) return this.summary
        this.summary = [
            id         : id, occurTime: startup, spend: System.currentTimeMillis() - startup.time,
            decision   : finalDecision, decisionId: decisionSpec.决策id, input: input,
            status     : status, exception : this.exception?.toString(),
            attrs      : data.collect {e ->
                 if (!e.key.matches("[a-zA-Z0-9]+") && attrManager.alias(e.key)) {
                     return null
                 }
                 if (e.value instanceof Optional) {
                     return [e.key, e.value.orElseGet({null})]
                 }
                 return e
            }.findAll {it} .collectEntries(),
            rules: rules.collect { r ->
                [attrs: r.attrs, decision: r.decision, data: r.data.collectEntries { e ->
                    String k = e.key
                    def v = e.value
                    if (v instanceof Optional) {v = v.orElseGet({null})}
                    if (!k.matches("[a-zA-Z0-9]+")) {
                        k = getAttrManager().alias(k)?:k
                    }
                    return [k, v]
            }]},
            dataCollectResult: dataCollectResult
        ]
        this.summary
    }


    /**
     * 用于接口返回
     * @return
     */
    Map<String, Object> result() {
        [
            id: id, decision: finalDecision, decisionId: decisionSpec.决策id,
            status: status,
            desc: exception?.toString(),
            attrs: decisionSpec.returnAttrs.collectEntries { n ->
                def v = data.get(n)
                if (v instanceof Optional) {v = v.orElseGet({null})}
                if (n.matches("[a-zA-Z0-9]+")) return [n, v] // 取英文属性名
                else {
                    [attrManager.alias(n)?:n, v]
                }
            }
         ]
    }


    @Override
    String toString() { result().toString() }
}

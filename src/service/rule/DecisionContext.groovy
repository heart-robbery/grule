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
    protected static final Logger    log             = LoggerFactory.getLogger(DecisionContext)
    // 执行上下文id
                    String           id
    // 开始时间
    final           Date             startup         = new Date()
                    DecisionSpec     decisionSpec
    protected       RuleSpec         curRuleSpec
    // 当前正在执行的规则
    protected       PassedRule       curPassedRule
    protected       PolicySpec       curPolicySpec
    // 执行过的规则记录
    protected final List<PassedRule> passedRules     = new LinkedList<>()
    // 最终决策结果
                    Decision         finalDecision
                    AttrManager      attrManager
                    PolicyManger     policyManager
                    EP  ep
    // 运行状态. TODO 以后做暂停时 running == false
    protected final def running = new AtomicBoolean(false)
    // 是否已启动
    protected final def started = new AtomicBoolean(false)
    // 是否执行结束
    protected final def end     = new AtomicBoolean(false)
    // 输入参数
    Map                 input
    // 最终数据属性值
    @Lazy protected Map data    = new Data(this)
    // 规则执行迭代器
    @Lazy protected def ruleItt = new RuleIterator(this)


    /**
     * 开始执行流程
     */
    void start() {
        if (!started.compareAndSet(false, true)) return
        log.info(logPrefix() + "开始")
        input?.forEach {k, v -> setAttr(k, v) }
        if (!ruleItt.hasNext()) {
            finalDecision = Decision.Reject
            log.warn(logPrefix() + "没有可执行的策略")
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
            while (running.get() && ruleItt.hasNext()) {
                def r = ruleItt.next()
                if (!r) break
                if (!r.enabled) continue
                def decision = decide(r)
                if (finalDecision == Decision.Review && decision == Decision.Accept) {
                    // 保留decision并继续往下执行
                } else finalDecision = decision
                if (Decision.Reject == finalDecision) break
            }
            if (Decision.Reject == finalDecision || (!ruleItt.hasNext())) {
                end.set(true); finalDecision = finalDecision?:Decision.Accept
                running.set(false); curPolicySpec = null; curPassedRule = null; curRuleSpec = null
                log.info(logPrefix() + "结束成功. 共执行: " + (System.currentTimeMillis() - startup.getTime()) + "ms "  + result())
                ep?.fire("decision.end", this)
            }
        } catch (ex) {
            end.set(true); finalDecision = Decision.Reject
            running.set(false); curPolicySpec = null; curPassedRule = null; curRuleSpec = null
            setAttr("errorCode", "EEEE")
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
        curPassedRule = new PassedRule(name: r.规则名, customId: r.规则id); passedRules.add(curPassedRule)
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
            log.debug(logPrefix() + "结束执行规则. decision: " + decision  + ", attrs: " + curPassedRule.attrs)
        } catch (ex) {
            decision = Decision.Reject
            log.error(logPrefix() + "规则执行错误. " + decision + ", attrs: " + curPassedRule.attrs)
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
        boolean f = curPassedRule?.attrs?.containsKey(aName)
        data.get(aName)
        if (!f) curPassedRule?.attrs?.remove(aName)
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
        if (curPassedRule && !end.get()) curPassedRule.attrs.put(key, value)
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
                log.debug(logPrefix() + "开始执行策略")
            }

            if (itt == null || !itt.hasNext()) itt = ctx.curPolicySpec.rules.iterator()
            if (itt.hasNext()) return itt.next()
            return null
        }
    }


    /**
     * 数据存放. 用于规则闭包执行上下文
     */
    protected class Data extends ConcurrentHashMap<String, Object> {
        final DecisionContext ctx

        Data(DecisionContext ctx) {this.ctx = ctx}

        @Override
        Object get(Object key) {
            if (key == null) return null
            def value = super.get(key)
            if (value == null) {// 属性值未找到,则从属性管理器获取
                put(key.toString(), Optional.empty()) // 代表属性已从外部获取过,后面就不再去获取了(防止重复获取)
                if (!ctx.end.get()) {
                    def v = ctx.getAttrManager().getAttr(key, ctx)
                    if (v instanceof Map) v.forEach((k, vv) -> convertSet(k, vv))
                    else convertSet(key, v)
                }
                value = super.get(key)
            }
            if (value instanceof Optional) {
                if (value.present) value = value.get()
                else value = null
            }
            ctx.ruleAttr(key, value)
            return value
        }


        /**
         * 设置属性
         * @param key
         * @param value
         */
        protected Object convertSet(String key, Object value) {
            if (value == null) value = Optional.empty()
            else {
                if (value instanceof Optional) {
                    value = value.present ? Optional.ofNullable(ctx.getAttrManager().convert(key, value.get())) : value
                } else {
                    value = ctx.getAttrManager().convert(key, value) // 属性值类型转换
                }
            }
            super.put(key, value)

            def n = ctx.getAttrManager().alias(key)
            if (n && n != key) super.put(n, value)
            value
        }


        @Override
        Object put(String key, Object value) {
            ctx.ruleAttr(key, convertSet(key, value))
            value
        }
    }


    /**
     * 执行过的规则
     */
    protected class PassedRule {
        String    name
        String    customId
        Decision  decision
        @Lazy Map<String, Object> attrs = new LinkedHashMap()

        @Override
        String toString() {
            return [name: name, customId: customId, decision: decision, attrs: attrs].toMapString()
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
    private Map summary
    Map summary() {
        if (this.summary && end.get()) return this.summary
        this.summary = [
            id         : id, occurTime: startup.time,
            decision   : finalDecision, decisionId: decisionSpec.决策id, input: input,
            attrs      : data.collect {e ->
                 if (!e.key.matches("[a-zA-Z0-9]+") && attrManager.alias(e.key)) {
                     return null
                 }
                 if (e.value instanceof Optional) {
                     return [e.key, e.value.orElseGet({null})]
                 }
                 return e
            }.findAll {it} .collectEntries(),
            rules: passedRules.collect {r ->
                [name: r.name, customId: r.customId, decision: r.decision, attrs: r.attrs.collectEntries {e ->
                    String k = e.key
                    def v = e.value
                    if (v instanceof Optional) {v = v.orElseGet({null})}
                    if (!k.matches("[a-zA-Z0-9]+")) {
                        k = getAttrManager().alias(k)?:k
                    }
                    return [k, v]
            }]}
        ]
        this.summary
    }


    /**
     * 用于接口返回
     * @return
     */
    Map result() {
        [id: id, decision: finalDecision, decisionId: decisionSpec.决策id,
         code: data.get('errorCode')?:'0000', // 错误码
         attrs: decisionSpec.returnAttrs.collectEntries { n ->
             def v = data.get(n)
             if (v instanceof Optional) {v = v.orElseGet({null})}
             if (n.matches("[a-zA-Z0-9]+")) return [n, v] // 取英文属性名
             else {
                 [attrManager.alias(n)?:n, v]
             }
         }]
    }


    @Override
    String toString() { result().toString() }
}

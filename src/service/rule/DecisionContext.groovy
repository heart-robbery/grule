package service.rule

import cn.xnatural.enet.event.EP
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import service.rule.spec.PolicySpec
import service.rule.spec.RuleSpec

import java.util.concurrent.atomic.AtomicBoolean

/**
 * 决策执行上下文
 */
class DecisionContext {
    protected static final Logger                         log     = LoggerFactory.getLogger(DecisionContext)
    // 决策执行标识(id)
                           String                         id
    // 开始时间
    final                  Date                    startup = new Date()
                    DecisionManager.DecisionHolder decisionHolder
    protected       RuleSpec                       curRuleSpec
    // 当前正在执行的规则
    protected       PassedRule                     curPassedRule
    protected       PolicySpec                     curPolicySpec
    // 执行过的规则记录
    protected final List<PassedRule>               rules   = new LinkedList<>()
    // 最终决策结果
                    DecisionEnum                   decisionResult
                    FieldManager                   fieldManager
                    EP                             ep
    // 运行状态. TODO 以后做暂停时 running == false
    protected final def                            running           = new AtomicBoolean(false)
    // 是否已启动
    protected final def                            started           = new AtomicBoolean(false)
    // 是否执行结束
    protected final def                            end               = new AtomicBoolean(false)
    // 输入参数
                           Map<String, Object>     input
    // 最终数据属性值
    @Lazy protected Map<String, Object> data              = new Data(this)
    // 数据收集器名 -> 数据收集结果集. 不用 ConcurrentHashMap 因为不能放null值
    @Lazy protected Map<String, Object> dataCollectResult = new LinkedHashMap<>()
    // 规则执行迭代器
    @Lazy protected def                                   ruleIterator      = new RuleIterator(this)
    // 执行结果 异常
    protected              Exception                      exception
    // 执行状态
    protected              String                         status            = '0001'


    /**
     * 开始执行流程
     */
    void start() {
        if (!started.compareAndSet(false, true)) return
        log.info(logPrefix() + "开始")
        input?.forEach {k, v -> setAttr(k, v) }
        decisionHolder.spec.operateFn?.call(data) //预执行操作
        if (!ruleIterator.hasNext()) {
            decisionResult = DecisionEnum.Reject
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
                if (decisionResult == DecisionEnum.Review && decision == DecisionEnum.Accept) {
                    // 保留decision并继续往下执行
                } else decisionResult = decision
                if (DecisionEnum.Reject == decisionResult) break
            }
            if (DecisionEnum.Reject == decisionResult || !ruleIterator.hasNext()) {
                decisionResult = decisionResult?:DecisionEnum.Accept; status = '0000'
                end.set(true);  running.set(false)
                curPolicySpec = null; curPassedRule = null; curRuleSpec = null
                log.info(logPrefix() + "结束成功. 共执行: " + (System.currentTimeMillis() - startup.time) + "ms "  + result())
                ep?.fire("decision.end", this)
            }
        } catch (ex) {
            decisionResult = DecisionEnum.Reject; status = 'EEEE'; this.exception = ex
            end.set(true); running.set(false)
            curPolicySpec = null; curPassedRule = null; curRuleSpec = null
            log.error(logPrefix() + "结束错误. 共执行: " + (System.currentTimeMillis() - startup.getTime()) + "ms " + result(), ex)
            ep?.fire("decision.end", this)
        }
    }


    /**
     * 规则决策
     * @param RuleSpec 规则描述
     * @return Decision 决策结果
     */
    protected DecisionEnum decide(RuleSpec r) {
        if (!r.enabled) return null
        curRuleSpec = r
        curPassedRule = new PassedRule(attrs: [规则名: r.规则名, *:r.attrs]); rules.add(curPassedRule)
        log.trace(logPrefix() + "开始执行规则")

        DecisionEnum decision
        try {
            r.decisionFn?.forEach(e -> {
                def d = e.v2.call(this.data)
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
                } else if ("Clear" == e.v1) {
                    log.trace(logPrefix() + "清除函数执行完成")
                } else {
                    log.trace(logPrefix() + e.v1 + "函数执行完成. " + d)
                }
            })
            log.debug(logPrefix() + "结束执行规则. decision: " + decision  + ", attrs: " + curPassedRule.data)
        } catch (ex) {
            decision = DecisionEnum.Reject
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
     * @param key 属性key
     * @param value 属性值
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
        Iterator<PolicySpec> policyItt = ctx.getDecisionHolder().spec.policies.iterator()
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
                ctx.curPolicySpec.operateFn?.call(ctx.data) //策略预执行操作
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
            //函数名
            if (ctx.decisionHolder.spec.functions.containsKey(aName)) return null

            def value = super.get(aName)
            if (value == null && !super.containsKey(aName) && !ctx.end.get()) {// 属性值未找到,则从属性管理器获取
                safeSet(aName.toString(), null) // 代表属性已从外部获取过,后面就不再去获取了(防止重复获取)
                safeSet((String) aName, ctx.getFieldManager().dataCollect(aName.toString(), ctx))
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
                value = value.present ? Optional.ofNullable(ctx.getFieldManager().convert(key, value.get())) : value
            } else {
                value = ctx.getFieldManager().convert(key, value) // 属性值类型转换
            }
            super.put(key, value)

            def n = ctx.getFieldManager().alias(key)
            if (n && n != key) super.put(n, value)
            value
        }

        @Override
        Object put(String key, Object value) {
            ctx.ruleAttr(key, safeSet(key, value))
            value
        }

        @Override
        Object remove(Object key) { // 删除缓存
            def r = super.remove(key)
            def field = ctx.getFieldManager().fieldMap.get(key)
            if (field) ctx.dataCollectResult.remove(field.dataCollector)
            return r
        }
    }


    /**
     * 执行过的规则
     */
    protected class PassedRule {
        // 规则 最终决策
              DecisionEnum        decision
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
        "[${id? "$id, " :''}${decisionHolder.decision.decisionId? "决策: $decisionHolder.spec.决策id" :''}${curPolicySpec? ", 策略: ${curPolicySpec.策略名}" :''}${curRuleSpec ? ", 规则: ${curRuleSpec.规则名}" :''}] -> "
    }


    /**
     * 整条决策 所有信息
     * @return
     */
    private Map<String, Object> _summary
    Map<String, Object> summary() {
        if (this._summary && end.get()) return this._summary
        this._summary = [
            decideId         : id, occurTime: startup, spend: System.currentTimeMillis() - startup.time,
            decision         : decisionResult, decisionId: decisionHolder.decision.decisionId, input: input,
            status           : status, exception: this.exception?.toString(),
            attrs            : data.collect { e ->
                def field = fieldManager.fieldMap.get(e.key)
                if (field && field.cnName == e.key) return null //去重复记录(去对应的中文, 保留对应的英文)
                if (e.value instanceof Optional) {
                    return [e.key, e.value.orElseGet({ null })]
                }
                return e
            }.findAll { it }.collectEntries(),
            rules            : rules.collect { r ->
                [attrs: r.attrs, decision: r.decision, data: r.data.collectEntries { e ->
                    String k = e.key
                    def v = e.value instanceof Optional ? e.value.orElseGet({ null }) : e.value
                    def field = fieldManager.fieldMap.get(e.key)
                    if (field && field.cnName == e.key) { //如果key是中文, 则翻译成对应的英文名
                        k = field.enName
                    }
                    return [k, v]
                }]
            },
            dataCollectResult: dataCollectResult
        ]
        this._summary
    }


    /**
     * 用于接口返回
     * @return
     */
    Map<String, Object> result() {
        [
            decideId: id, decision: decisionResult, decisionId: decisionHolder.decision.decisionId,
            status  : status,
            desc    : exception?.toString(),
            attrs   : end.get() ? decisionHolder.spec.returnAttrs.collectEntries { name ->
                def v = data.get(name)
                if (v instanceof Optional) {
                    v = v.orElseGet({ null })
                }
                def field = fieldManager.fieldMap.get(name)
                //如果key是中文, 则翻译成对应的英文名
                if (field && field.cnName == name) return [field.enName, v]
                else return [name, v]
            } : null
        ]
    }


    @Override
    String toString() { result().toString() }
}

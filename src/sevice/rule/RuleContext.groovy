package sevice.rule

import cn.xnatural.enet.event.EP
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import sevice.rule.spec.PolicySetSpec
import sevice.rule.spec.PolicySpec
import sevice.rule.spec.RuleSpec

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 一条决策执行上下文
 */
class RuleContext {
    protected static final Logger log = LoggerFactory.getLogger(RuleContext)

    protected final Map<String, Boolean> attrGettingMap = new ConcurrentHashMap<>()
    // 执行上下文id
                    String               id
    // 开始
    final           Date                 startup = new Date()
                    PolicySetSpec        pss
    protected       RuleSpec             curRuleSpec
    protected       PassedRule           curPassedRule
    protected       PolicySpec           curPolicySpec
    protected final List<PassedRule>     passedRules    = new LinkedList<>()
                    Decision             finalDecision
                    RuleEngine           re
                    AttrManager          am
                    PolicyManger         pm
                    EP                   ep
                    Executor             exec
    // 运行状态
    protected final def                  running        = new AtomicBoolean(false)
    // 是否已启动
    protected final def                            started = new AtomicBoolean(false)
    protected final def                            end = new AtomicBoolean(false)
    // 数据属性
    @Lazy protected Map                  data           = new Data(this)
    // 执行迭代器
    @Lazy protected def                            itt = new Itt(this)


    /**
     * 开始执行流程
     */
    void start() {
        if (!started.compareAndSet(false, true)) return
        log.info(logPrefixFn() + "开始")
        trigger()
    }


    /**
     * 触发执行流程
     */
    protected final void trigger() {
        if (!running.compareAndSet(false, true)) return
        try {
            while (running.get() && itt.hasNext()) {
                def r = itt.next()
                if (!r) break
                if (!r.enabled) continue
                finalDecision = decide(r)
                if (Decision.Reject == finalDecision) break
            }
            if ((Decision.Reject == finalDecision) || (!itt.hasNext())) {
                finalDecision = finalDecision?:Decision.Accept
                running.set(false); curPolicySpec = null; curPassedRule = null; curRuleSpec = null
                log.info(logPrefixFn() + "结束成功. " + summary())
                end.set(true)
                ep?.fire("end-rule-ctx", this)
                end()
            }
        } catch (Exception ex) {
            finalDecision = Decision.Reject
            running.set(false); curPolicySpec = null; curPassedRule = null; curRuleSpec = null
            log.error(logPrefixFn() + "结束错误. " + summary(), ex)
            end.set(true)
            ep?.fire("end-rule-ctx", this)
            end()
        }
    }


    // 执行结束
    protected void end() {}


    /**
     * 规则决策
     * @param RuleSpec 规则
     * @return Decision
     */
    protected Decision decide(RuleSpec r) {
        if (!r.enabled) return null

        curRuleSpec = r
        curPassedRule = new PassedRule(name: r.规则名, customId: r.规则id); passedRules.add(curPassedRule)
        log.debug(logPrefixFn() + "开始执行规则")

        Decision decision
        try {
            r.decisionFn?.forEach((k, fn) -> {
                def d = fn.call(this.data)
                if ("Reject" == k) {
                    decision = d
                    log.trace(logPrefixFn() + "拒绝函数执行结果: " + d)
                } else if ("Accept" == k) {
                    decision = d
                    log.trace(logPrefixFn() + "通过函数执行结果: " + d)
                } else if ("Review" == k) {
                    decision = d
                    log.trace(logPrefixFn() + "人工审核函数执行结果: " + d)
                } else if ("Operate" == k) {
                    log.trace(logPrefixFn() + "操作函数执行完成")
                } else {
                    log.trace(logPrefixFn() + k + "函数执行完成. " + d)
                }
            })
            log.debug(logPrefixFn() + "结束执行规则. " + decision)
        } catch (Exception ex) {
            decision = Decision.Reject
            log.error(logPrefixFn() + "规则执行错误. 拒绝")
            throw ex
        } finally {
            curPassedRule.setDecision(decision); curRuleSpec = null; curPassedRule = null
        }
        return decision
    }


    Object getAttr(String key) {
        boolean f = curPassedRule?.attrs?.containsKey(key)
        data.get(key)
        if (!f) curPassedRule?.attrs?.remove(key)
    }


    void setAttr(String name, Object value) { data.put(name, value) }


    /**
     * 设置为规则用到的属性
     * @param key
     * @param value
     */
    protected void ruleAttr(String key, Object value) {
        if (curPassedRule) curPassedRule.attrs.put(key, value)
    }


    /**
     * 执行迭代器
     */
    protected class Itt implements Iterator<RuleSpec> {
        final RuleContext ctx

        Itt(RuleContext ctx) {if (ctx == null) throw new NullPointerException("Ctx is null"); this.ctx = ctx}

        // 策略迭代器
        Iterator<PolicySpec> psIt = ctx.getPss().ps.stream()
            .map((s) -> {ctx.getPm().findPolicy(s)}).filter(p -> p != null).iterator()
        Iterator<RuleSpec> rIt

        @Override
        boolean hasNext() { psIt.hasNext() || (rIt && rIt.hasNext()) }

        @Override
        RuleSpec next() {
            if ((ctx.curPolicySpec == null || (rIt == null || !rIt.hasNext())) && psIt.hasNext()) {
                ctx.curPolicySpec = psIt.next()
                log.debug(logPrefixFn() + "开始执行策略")
            }

            if (rIt == null || !rIt.hasNext()) rIt = ctx.curPolicySpec.rs.iterator()
            if (rIt.hasNext()) return rIt.next()
            return null
        }
    }


    /**
     * 数据存放
     */
    protected class Data extends ConcurrentHashMap {
        final RuleContext ctx

        Data(RuleContext ctx) {this.ctx = ctx}

        @Override
        Object get(Object key) {
            def r = super.get(key)
            if (r == null) {
                put(key, null)
                if (!ctx.end.get()) { // 外部数据获取
                    def v = ctx.getAm().getAttr(key, ctx)
                    if (v instanceof Map) v.forEach((k, vv) -> put(k, vv))
                    else put(key, v)
                }
            }
            r = super.get(key)
            if (r instanceof Optional) {
                if (r.present) r = r.get()
                else r = null
            }
            ctx.ruleAttr(key, r)
            return r
        }

        @Override
        Object put(Object key, Object value) {
            if (value == null) value = Optional.empty()
            else {
                if (value instanceof Optional) {
                    value = Optional.ofNullable(ctx.getAm().convert(value.orElseGet({null})))
                } else {
                    value = ctx.getAm().convert(key, value)
                }
            }
            super.put(key, value)
            def n = ctx.getAm().alias(key)
            if (n && n != key) super.put(n, value)

            ctx.ruleAttr(key, value)
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
        @Lazy Map<String, Object> attrs = new LinkedHashMap(7)

        @Override
        String toString() {
            return [name: name, customId: customId, decision: decision, attrs: attrs].toMapString()
        }
    }


    // 日志前缀
    protected String logPrefixFn() {
        "[${id? "$id, " :''}${pss.策略集名?:''}${curPolicySpec? ", 策略: ${curPolicySpec.策略名}" :''}${curRuleSpec ? ", 规则: ${curRuleSpec.规则名}" :''}] -> "
    }


    /**
     * 整条决策 所有信息
     * @return
     */
    Map summary() {
        [id: id, finalDecision: finalDecision, policySetName: pss.策略集名,
         attrs: data.findAll {e ->
             if (e.key.matches("[a-zA-Z]+")) return e
             null
         },
         passedRules: passedRules.collect {r ->
            [name: r.name, customId: r.customId, decision: r.decision, attrs: r.attrs.collectEntries {e ->
                if (e.key.matches("[a-zA-Z]+")) return e
                else [am.alias(e.key), e.value]
            }]
        }]
    }


    /**
     * 用于接口返回
     * @return
     */
    Map result() {
        [id: id, finalDecision: finalDecision, policySetName: pss.策略集名, attrs: pss.returnAttrs.collectEntries {n ->
            if (n.matches("[a-zA-Z]+")) return [n, data.get(n)]
            else {
                def en = am.alias(n)
                if (en) return [en, data.get(en)]
                else [n, data.get(n)]
            }
        }]
    }


    @Override
    String toString() { result().toString() }
}

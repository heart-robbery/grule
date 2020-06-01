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
class RuleContext extends GroovyObjectSupport {
    protected static final Logger log = LoggerFactory.getLogger(RuleContext)

    protected final Map<String, Boolean> attrGettingMap = new ConcurrentHashMap<>()
    // 执行上下文id
                    String               id
    // 开始
    final           Date                 startup = new Date()
    protected final Map                  data           = new ConcurrentHashMap()
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
    protected       boolean              pause
    // 是否已启动
    final def                            started = new AtomicBoolean(false)
    // 执行迭代器
    @Lazy def                            itt = new Iterator<RuleSpec>() {
        RuleContext ctx = RuleContext.this
        // 策略迭代器
        Iterator<PolicySpec> psIt = ctx.getPss().ps.collect {s -> ctx.getPm().findPolicy(s)}.findAll {it}.iterator()
        Iterator<RuleSpec> rIt
        @Override
        boolean hasNext() { psIt.hasNext() || (rIt && rIt.hasNext()) }

        @Override
        RuleSpec next() {
            if (ctx.@pause) return ctx.@curPassedRule // 暂停的时候返回当前正在执行的规则
            if ((ctx.@curPolicySpec == null || (rIt == null || !rIt.hasNext())) && psIt.hasNext()) {
                ctx.@curPolicySpec = psIt.next()
                log.debug(logPrefixFn() + "开始执行策略")
            }

            if (rIt == null || !rIt.hasNext()) rIt = ctx.@curPolicySpec.rs.iterator()
            if (rIt.hasNext())  return rIt.next()
            return null
        }
    }


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
        if (!this.@running.compareAndSet(false, true)) return
        try {
            while (this.@running.get() && !this.@pause && getItt().hasNext()) {
                def r = getItt().next()
                if (!r) break
                if (!r.enabled) continue
                decide(r)
            }
            if (!getItt().hasNext() && !this.@pause) {
                this.@finalDecision = this.@finalDecision?:Decision.Accept
                this.@running.set(false); this.@curPolicySpec = null; this.@curPassedRule = null; this.@curRuleSpec = null
                log.info(logPrefixFn() + "结束成功. " + summary())
                this.@ep?.fire("end-rule-ctx", this)
                end()
            }
        } catch (Exception ex) {
            this.@finalDecision = Decision.Reject
            this.@running.set(false); this.@curPolicySpec = null; this.@curPassedRule = null; this.@curRuleSpec = null
            log.error(logPrefixFn() + "结束错误. " + summary(), ex)
            this.@ep?.fire("end-rule-ctx", this)
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

        this.@curRuleSpec = r
        this.@curPassedRule = new PassedRule()
        this.@curPassedRule.setCustomId(r.规则id); this.@curPassedRule.setName(r.规则名)
        this.@passedRules.add(curPassedRule)
        log.debug(logPrefixFn() + "开始执行规则")

        Decision decision
        try {
            r.decisionFn?.forEach((k, fn) -> {
                def d = fn.call(this)
                if ("Reject" == k) {
                    decision = d
                    log.trace(logPrefixFn() + "拒绝函数执行结果: " + d)
                } else if ("Accept" == k) {
                    decision = d
                    log.trace(logPrefixFn() + "通过函数执行结果: " + d)
                } else if ("Review" == k) {
                    decision = d
                    log.trace(logPrefixFn() + "人工审核函数执行结果: " + d)
                } else if ("Operate-set" == k) {
                    log.trace(logPrefixFn() + "赋值操作函数执行完成")
                } else {
                    log.trace(logPrefixFn() + k + "函数执行完成. " + d)
                }
            })
            log.debug(logPrefixFn() + "结束执行规则. decision: " + decision)
        } catch (Exception ex) {
            decision = Decision.Reject
            log.error(logPrefixFn() + "规则执行错误. 拒绝")
            throw ex
        } finally {
            this.@curPassedRule.setDecision(decision); this.@curRuleSpec = null; this.@curPassedRule = null
        }
        return decision
    }


    @Override
    Object getProperty(String key) {
        if (!this.@data.containsKey(key)) { // 所有不存在的属性获取都异步
            this.@data.put(key, Optional.empty())
            suspend()
            this.@attrGettingMap.put(key, false)
            this.@am.populateAttr(key, this)
        }
        def r = this.@data.get(key)
        if (r instanceof Optional) {
            if (r.present) r = r.get()
            else r = null
        }
        this.@curPassedRule?.attrs?.put(key, r)
        return r
    }


    @Override
    void setProperty(String name, Object value) {
        boolean f = false
        if (this.@attrGettingMap.containsKey(name)) {
            this.@attrGettingMap.put(name, true)
            f = true
        }
        if (value == null) value = Optional.empty()
        this.@data.put(name, value)
        def n = this.@am.alias(name)
        if (n && n != name) this.@data.put(n, value)
        if (f) tryResume()
        // super.setProperty(name, value)
    }


    class PassedRule {
        String    name
        String    customId
        // String    desc
        Decision  decision
        @Lazy Map attrs = new LinkedHashMap(7)
    }


    /**
     * 暂停
     */
    void suspend() {this.@pause = true; this.@running.set(false)}


    /**
     * 尝试恢复执行
     */
    void tryResume() {
        // 当前正在执行的规则中如果还有未取成功的属性, 则暂时不恢复执行
        if (this.@curPassedRule?.attrs?.find {e ->
            (this.@attrGettingMap.containsKey(e.key) && !this.@attrGettingMap.get(e.key))
        }) return
        this.@pause = false
        trigger()
    }


    // 日志前缀
    protected String logPrefixFn() {
        "[${getId()? "${getId()}, " :''}${getPss().策略集名?:''}${this.@curPolicySpec? ", 策略: ${this.@curPolicySpec.策略名}" :''}${this.@curRuleSpec ? ", 规则: ${this.@curRuleSpec.规则名}" :''}] -> "
    }


    Map summary() {
        [id: id, finalDecision: finalDecision, policySetName: pss.策略集名, passedRules: passedRules]
    }


    Map result() {
        [id: id, finalDecision: finalDecision, policySetName: pss.策略集名, passedRules: passedRules]
    }


    @Override
    String toString() { result().toString() }
}

package sevice.rule

import cn.xnatural.enet.event.EP
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import sevice.rule.spec.PolicySetSpec
import sevice.rule.spec.PolicySpec
import sevice.rule.spec.RuleSpec

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 一条决策执行上下文
 */
class RuleContext extends GroovyObjectSupport {
    protected static final Logger log = LoggerFactory.getLogger(RuleContext)

    @Lazy protected Map<String, Boolean> attrGetterMap = new ConcurrentHashMap<>()
    // 执行id
    String id
    final Map data = new ConcurrentHashMap()
    PolicySetSpec pss
    protected RuleSpec     curRuleSpec
    protected PassedRule   curPassedRule
    protected PolicySpec curPolicySpec
    /**
     * 跳到下一个要执行的 规则自定义id
     */
    String                 nextCustomId
    final List<PassedRule> passedRules = new LinkedList<>()
    Decision               finalDecision
    RuleEngine re
    AttrManager            am
    PolicyManger           pm
    EP ep
    // 运行状态
    final def running = new AtomicBoolean(false)
    boolean pause
    // 是否已启动
    final def started = new AtomicBoolean(false)
    // 日志前缀
    @Lazy def logPrefixFn = {"[${id? "$id, " :''}${pss.策略集名?:''}${curPolicySpec? ", 策略: $curPolicySpec.策略名" :''}${curRuleSpec ? ", 规则: $curRuleSpec.规则名" :''}] -> ".toString()}


    // 执行迭代器
    @Lazy protected def itt = new Iterator<RuleSpec>() {
        // 策略迭代器
        @Lazy Iterator<PolicySpec> psIt = pss.ps.collect {s -> pm.findPolicy(s)}.findAll {it}.iterator()
        Iterator<RuleSpec> rIt
        @Override
        boolean hasNext() { psIt.hasNext() || (rIt && rIt.hasNext()) }

        @Override
        RuleSpec next() {
            if (!running.get()) return curPassedRule // 暂停的时候返回当前正在执行的规则
            if ((curPolicySpec == null || (rIt == null || !rIt.hasNext())) && psIt.hasNext()) {
                curPolicySpec = psIt.next()
                log.debug(logPrefixFn() + "开始执行策略")
            }

            if (rIt == null || !rIt.hasNext()) rIt = curPolicySpec.rs.iterator()
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
        if (!running.compareAndSet(false, true)) return

        while (running.get() && !pause && itt.hasNext()) {
            def r = itt.next()
            if (!r) break
            if (!r.enabled) continue
            decide(r)
        }
        if (!itt.hasNext()) {
            running.set(false)
            curPolicySpec = null; curPassedRule = null; curPolicySpec = null
            end()
        }
    }


    protected void end() {
        log.info(logPrefixFn() + "结束. " + summary())
        ep?.fire("end-rule-ctx", this)
    }



    /**
     * 规则决策
     * @param RuleSpec 规则
     * @return Decision
     */
    protected Decision decide(RuleSpec r) {
        if (!r.enabled) return null

        curRuleSpec = r; curPassedRule = new PassedRule(customId: r.规则id, name: r.规则名); passedRules.add(curPassedRule)
        log.debug(logPrefixFn() + "开始执行规则")

        Decision decision
        try {
            r.decisionFn?.each {e ->
                def d = e.value.call(this)
                decision = (d?:decision)
            }
        } catch(Exception ex) {
            decision = Decision.Reject
            log.error(logPrefixFn() + "规则执行错误", ex)
        }

        curPassedRule.decision = decision; curRuleSpec = null; curPassedRule = null
        return decision
    }


    Object getProperty(String key) {
        if (!data.containsKey(key)) { // 所有不存在的属性获取都异步
            data.put(key, Optional.empty())
            suspend()
            attrGetterMap.put(key, false)
            am.populateAttr(key, this)
        }
        def r = data.get(key)
        if (r instanceof Optional) {
            if (r.present) r = r.get()
            else r = null
        }
        curPassedRule?.attrs?.put(key, r)
        return r
    }


    void setProperty(String name, Object value) {
        boolean f = false
        if (attrGetterMap.containsKey(name)) {
            attrGetterMap.put(name, true)
            f = true
        }
        if (value == null) value = Optional.empty()
        data.put(name, value)
        def n = am.alias(name)
        if (n && n != name) data.put(n, value)
        if (f) tryResume()
        // super.setProperty(name, value)
    }


    class PassedRule {
        String    name
        String    customId
        // String    desc
        Decision  decision
        @Lazy Map attrs = new LinkedHashMap(7)
        transient Map<String, Closure> decisionFn = new LinkedHashMap<>()
    }


    // 暂停
    RuleContext suspend() {pause = true; running.set(false); this}

    RuleContext tryResume() {
        if (!pause) return this
        // 当前正在执行的规则中如果还有未取成功的属性, 则暂时不恢复执行
        if (curPassedRule?.attrs?.find {e ->
            (attrGetterMap.containsKey(e.key) && !attrGetterMap.get(e.key))
        }) return this
        pause = false
        trigger()
        this
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

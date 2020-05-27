package sevice.rule

import sevice.rule.spec.PolicySpec
import sevice.rule.spec.RuleSpec

/**
 * 执行上下文
 */
class ExecutionContext extends LinkedHashMap {

    protected RuleSpec     curRuleSpec
    protected PassedRule   curPassedRule
    /**
     * 跳到下一个要执行的 规则自定义id
     */
    String                 nextCustomId
    final List<PassedRule> passedRule = new LinkedList<>()
    AttrManager            attrManager
    Decision finalDecision
    List<PolicySpec> ps


    def start() {
        if (!ps) throw new IllegalArgumentException("未配置策略")
    }


    @Override
    Object get(Object key) {
        if (!containsKey(key)) {
            put(key, null)
            def v = attrManager.get(key, this)
            if (v instanceof Map) {putAll(v)}
        }
        def r = super.get(key)
        curPassedRule?.usedAttrs.put(key, r)
        return r
    }


    class PassedRule {
        String    name
        String    customId
        String    desc
        Decision decision
        @Lazy Map usedAttrs = new LinkedHashMap()
    }


    def begin(RuleSpec r) {
        this.curRuleSpec = r
        curPassedRule = new PassedRule(customId: r.规则id, name: r.规则名, desc: r.规则描述)
        passedRule.add(curPassedRule)
    }


    def end(RuleSpec r, Decision decision) {
        curPassedRule.decision = decision
        curRuleSpec = null
        curPassedRule = null
    }
}

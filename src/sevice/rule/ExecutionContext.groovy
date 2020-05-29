package sevice.rule

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature
import sevice.rule.spec.PolicySetSpec
import sevice.rule.spec.RuleSpec

import java.util.concurrent.ConcurrentHashMap

/**
 * 执行上下文
 */
class ExecutionContext extends GroovyObjectSupport {

    // 执行id
    String id
    final Map data = new ConcurrentHashMap()
    PolicySetSpec pss
    protected RuleSpec     curRuleSpec
    protected PassedRule   curPassedRule
    /**
     * 跳到下一个要执行的 规则自定义id
     */
    String                 nextCustomId
    final List<PassedRule> passedRules = new LinkedList<>()
    Decision               finalDecision
    AttrManager            am
    PolicyManger           pm


    @Override
    Object getProperty(String key) {
        if (!data.containsKey(key)) {
            data.put(key, null)
            def v = am.get(key, this)
            if (v instanceof Map) {v.each {e -> this.setProperty(e.key, e.value)}}
            else setProperty(key, v)
        }
        def r = data.get(key)
        curPassedRule?.attrs?.put(key, r)
        return r
    }


    @Override
    void setProperty(String name, Object value) {
        data.put(name, value)
        def n = am.alias(name)
        if (n && n != name) data.put(n, value)
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
     * 开始执行规则
     * @param r
     * @return
     */
    def begin(RuleSpec r) {
        curRuleSpec = r
        curPassedRule = new PassedRule(customId: r.规则id, name: r.规则名)
        passedRules.add(curPassedRule)
    }


    /**
     * 结束执行规则
     * @param r
     * @param decision
     * @return
     */
    def end(RuleSpec r, Decision decision) {
        curPassedRule.decision = decision
        curRuleSpec = null
        curPassedRule = null
    }


    String summary() {
        JSON.toJSONString([id: id, finalDecision: finalDecision, policySetName: pss.策略集名, passedRules: passedRules], SerializerFeature.WriteMapNullValue)
    }

    @Override
    String toString() {

    }
}

package sevice.rule.spec

import groovy.transform.TailRecursive
import sevice.rule.Decision
import sevice.rule.ExecutionContext


class PolicySpec {
    String               策略名
    String               策略描述
    @Lazy List<RuleSpec> rs = new LinkedList<>()



    def 规则定义(@DelegatesTo(value = RuleSpec, strategy = Closure.DELEGATE_ONLY) Closure cl) {
        RuleSpec rule = new RuleSpec(); rs.add(rule)
        def code = cl.rehydrate(rule, rule, rule)
        // cl.resolveStrategy = Closure.DELEGATE_ONLY
        // code()
    }


    /**
     * 规则决策
     * @param ctx 执行上下文
     * @return Decision
     */
    Decision decide(ExecutionContext ctx) { return decide0(ctx, rs) }

    @TailRecursive
    protected Decision decide0(ExecutionContext ctx, List<RuleSpec> rs, int limit) {
        if (limit > 5) throw new RuntimeException("策略'$策略名'循环过多")
        Decision decision
        for (RuleSpec r : rs) {
            decision = r.decide(ctx)
            if (Decision.Reject == decision) break
            if (ctx.nextCustomId) {
                limit++
                def cId = ctx.nextCustomId; ctx.nextCustomId = null
                decide0(this.rs.dropWhile {rr -> rr.规则id == cId })
            }
        }
        return decision
    }
}

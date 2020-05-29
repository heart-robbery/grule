package sevice.rule


import core.module.ServerTpl
import sevice.rule.spec.PolicySpec
import sevice.rule.spec.RuleSpec

import java.util.function.Consumer

/**
 * 规则执行引擎
 */
class RuleEngine extends ServerTpl {
    @Lazy def psm = bean(PolicySetManager)


    /**
     * 执行策略集
     * @param policySetName
     * @param async 是否异步
     * @param id id
     * @param params 参数
     * @return
     */
    String run(String policySetName, boolean async, String id = null, Map params = []) {
        // TODO
        ExecutionContext ctx = new ExecutionContext(
            id: (id?:UUID.randomUUID().toString().replaceAll('-', '')),
            pm: bean(PolicyManger), am: bean(AttrManager), pss: psm.findPolicySet(policySetName)
        )
        params.each {e -> ctx.setProperty(e.key, e.value)}
        decide(ctx)

        ctx.toString()
    }


    /**
     * 决策
     * @param ctx 执行上下文
     * @return 最终决策 {@link Decision}
     */
    Decision decide(ExecutionContext ctx) {
        def pss = ctx.getPss()
        if (!pss.ps) throw new IllegalArgumentException("策略列表为空. " + pss.策略集名)
        Decision decision
        for (String pn : pss.ps) {
            def p = ctx.pm.findPolicy(pn)
            decision = decide(ctx, p)
            if (decision == Decision.Reject) break
        }
        ctx.finalDecision = (decision == null ? Decision.Accept : decision)
        ctx.finalDecision
    }


    /**
     * 策略决策
     * @param ctx 执行上下文
     * @param PolicySpec 策略
     * @return Decision
     */
    Decision decide(ExecutionContext ctx, PolicySpec p) {
        decide0(ctx, p.rs)
    }


    // @TailRecursive
    protected Decision decide0(ExecutionContext ctx, List<RuleSpec> rs, int limit = 0) {
        if (limit > 5) throw new RuntimeException("策略'$策略名'循环过多")
        Decision decision
        for (RuleSpec r : rs) {
            decision = decide(ctx, r)
            if (Decision.Reject == decision) break
            if (ctx.nextCustomId) {
                limit++
                def cId = ctx.nextCustomId; ctx.nextCustomId = null
                decide0(this.rs.dropWhile {rr -> rr.规则id == cId })
            }
        }
        return decision
    }


    /**
     * 规则决策
     * @param ctx 执行上下文
     * @param RuleSpec 规则
     * @return Decision
     */
    Decision decide(ExecutionContext ctx, RuleSpec r) {
        if (!r.enabled) return null
        ctx.begin(r)
        Decision decision
        r.decisionFn?.each {fn ->
            fn.resolveStrategy = Closure.DELEGATE_FIRST
            fn.delegate = ctx
            def d = fn.call(ctx)
            decision = (d?:decision)
        }
        ctx.end(r, decision)
        return decision
    }
}

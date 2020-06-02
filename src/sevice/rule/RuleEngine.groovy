package sevice.rule

import cn.xnatural.enet.event.EL
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature
import core.module.ServerTpl

import java.util.function.Consumer

/**
 * 规则执行引擎
 */
class RuleEngine extends ServerTpl {
    @Lazy def psm = bean(PolicySetManager)


    @EL(name = 'end-rule-ctx', async = true)
    void endCtx(RuleContext ctx) {
        log.info("end rule ctx: " + JSON.toJSONString(ctx.summary(), SerializerFeature.WriteMapNullValue))
    }


    /**
     * 执行策略集
     * @param policySetId
     * @param async 是否异步
     * @param id id
     * @param params 参数
     * @return
     */
    Map run(String policySetId, boolean async = true, String id = null, Map params = []) {
        // TODO
        RuleContext ctx = new RuleContext()
        ctx.setId(id?:UUID.randomUUID().toString().replaceAll('-', ''))
        ctx.setPss(psm.findPolicySet(policySetId))
        ctx.setPm(bean(PolicyManger))
        ctx.setAm(bean(AttrManager))
        ctx.setEp(ep)
        ctx.setExec(exec)

        log.info("Run policy. policySet: " + policySetId + ", async: " + async + ", id: " + ctx.getId() + ", params: " + params)
        params.forEach{k, v -> ctx.setAttr(k, v) }
        if (async) super.async { ctx.start() }
        else ctx.start()
        return ctx.result()
    }


//    /**
//     * 决策
//     * @param ctx 执行上下文
//     * @return 最终决策 {@link Decision}
//     */
//    Decision decide(RuleContext ctx) {
//        def pss = ctx.getPss()
//        if (!pss.ps) throw new IllegalArgumentException("策略列表为空. " + pss.策略集名)
//        Decision decision
//        for (String pn : pss.ps) {
//            def p = ctx.getPm().findPolicy(pn)
//            decision = decide(ctx, p)
//            if (decision == Decision.Reject) break
//        }
//        ctx.setFinalDecision(decision == null ? Decision.Accept : decision)
//        ctx.getFinalDecision()
//    }
//
//
//    /**
//     * 策略决策
//     * @param ctx 执行上下文
//     * @param PolicySpec 策略
//     * @return Decision
//     */
//    Decision decide(RuleContext ctx, PolicySpec p) {
//        decide0(ctx, p.rs)
//    }
//
//
//    // @TailRecursive
//    protected Decision decide0(RuleContext ctx, List<RuleSpec> rs, int limit = 0) {
//        if (limit > 5) throw new RuntimeException("策略'$策略名'循环过多")
//        Decision decision
//        for (RuleSpec r : rs) {
//            decision = decide(ctx, r)
//            if (Decision.Reject == decision) break
//            if (ctx.nextCustomId) {
//                limit++
//                def cId = ctx.nextCustomId; ctx.nextCustomId = null
//                decide0(this.rs.dropWhile {rr -> rr.规则id == cId })
//            }
//        }
//        return decision
//    }
//
//
//    /**
//     * 规则决策
//     * @param ctx 执行上下文
//     * @param RuleSpec 规则
//     * @return Decision
//     */
//    Decision decide(RuleContext ctx, RuleSpec r) {
//        if (!r.enabled) return null
//        ctx.begin(r)
//        Decision decision
//        r.decisionFn?.each {e ->
//            def d = e.value.call(ctx)
//            decision = (d?:decision)
//        }
//        ctx.end(r, decision)
//        return decision
//    }
}

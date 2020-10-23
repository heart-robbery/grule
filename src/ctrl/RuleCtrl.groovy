package ctrl

import core.ServerTpl
import core.http.HttpContext
import core.http.mvc.ApiResp
import core.http.mvc.Ctrl
import core.http.mvc.Path
import core.jpa.BaseRepo
import dao.entity.DecisionResult
import service.rule.AttrManager
import service.rule.DecisionContext
import service.rule.DecisionManager
import service.rule.DecisionSrv

@Ctrl
class RuleCtrl extends ServerTpl {

    @Lazy def decisionSrv = bean(DecisionSrv)
    @Lazy def dm          = bean(DecisionManager)
    @Lazy def am = bean(AttrManager)
    @Lazy def repo        = bean(BaseRepo, 'jpa_rule_repo')


    /**
     * 执行一条决策
     */
    @Path(path = 'decision')
    ApiResp decision(String decisionId, HttpContext ctx) {
        if (!decisionId) {
            return ApiResp.fail('decisionId must not be empty')
        }
        def decision = dm.findDecision(decisionId)
        if (decision == null) {
            return ApiResp.fail("未找到决策: $decisionId")
        }
        Map<String, Object> params = ctx.params()
        log.info("Run decision. decisionId: " + decisionId + ", id: " + ctx.request.id + ", params: " + params)
        try {
            decision.paramValidator?.apply(params) // 参数验证
        } catch (IllegalArgumentException ex) {
            return ApiResp.fail(ex.message)
        }

        DecisionContext dCtx = new DecisionContext()
        dCtx.setDecisionHolder(decision)
        dCtx.setId(ctx.request.id)
        dCtx.setAttrManager(am)
        dCtx.setEp(ep)
        dCtx.setInput(params)
        repo.saveOrUpdate(new DecisionResult(id: dCtx.id, decisionId: decisionId, occurTime: dCtx.startup))

        boolean isAsync = Boolean.valueOf(params.getOrDefault('async', false).toString())
        if (isAsync) async { dCtx.start() }
        else dCtx.start()
        return ApiResp.ok(dCtx.result())
    }


    /**
     * 查询决策结果
     */
    @Path(path = 'findDecisionResult')
    ApiResp findDecisionResult(String id) {
        if (!id) return ApiResp.fail("id must not be empty")
        def dr = repo.findById(DecisionResult, id)
        if (!dr) return ApiResp.fail("未找到记录: " + id)
        ApiResp.ok(

        )
    }


    /**
     * 加载属性配置
     */
    @Path(path = 'loadAttrCfg')
    ApiResp loadAttrCfg() {
        async {
            bean(AttrManager).loadField()
            bean(AttrManager).loadDataCollector()
            ep.fire("wsMsg_rule", '加载完成')
        }
        ApiResp.ok('加载中...')
    }
}

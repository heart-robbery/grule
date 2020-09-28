package ctrl

import core.ServerTpl
import core.http.HttpContext
import core.http.mvc.ApiResp
import core.http.mvc.Ctrl
import core.http.mvc.Path
import core.jpa.BaseRepo
import dao.entity.DecisionResult
import service.rule.AttrManager
import service.rule.DecisionEngine

@Ctrl
class RuleCtrl extends ServerTpl {

    @Lazy def engine = bean(DecisionEngine)
    @Lazy def repo = bean(BaseRepo, 'jpa_rule_repo')


    /**
     * 执行一条决策
     */
    @Path(path = 'decision')
    void decision(HttpContext ctx) {
        Map<String, Object> params = ctx.params()
        String decisionId = params['decisionId']
        if (!decisionId) {
            ctx.render ApiResp.fail('decisionId must not be empty')
            return
        }
        boolean async = params['async'] == 'true'
        ctx.render(
            ApiResp.ok(
                engine.run(decisionId, async, ctx.request.id, params)
            )
        )
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

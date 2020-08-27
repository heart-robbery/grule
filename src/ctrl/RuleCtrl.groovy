package ctrl

import core.ServerTpl
import core.http.HttpContext
import core.http.mvc.ApiResp
import core.http.mvc.Ctrl
import core.http.mvc.Path
import service.rule.AttrManager
import service.rule.DecisionEngine
import service.rule.DecisionManager
import service.rule.PolicyManger

@Ctrl
class RuleCtrl extends ServerTpl {

    @Lazy def engine = bean(DecisionEngine)


    /**
     * 执行一条决策
     */
    @Path(path = 'decision')
    void decision(HttpContext ctx) {
        Map params = ctx.params()
        String decisionId = params['decisionId']
        if (!decisionId) {
            ctx.render ApiResp.fail('decisionId must not be empty')
            return
        }
        boolean async = params['async'] == 'true' ? true : false
        ctx.render(
            ApiResp.ok(
                engine.run(decisionId, async, ctx.request.id, params)
            )
        )
    }


    /**
     * 设置一条决策
     */
    @Path(path = 'setDecision', method = 'post')
    ApiResp setDecision(HttpContext ctx) {
        ApiResp.ok(
            bean(DecisionManager).create(ctx.request.bodyStr)
        )
    }


    /**
     * 设置一条策略
     */
    @Path(path = 'setPolicy', method = 'post')
    ApiResp setPolicy(HttpContext ctx) {
        ApiResp.ok(
            bean(PolicyManger).create(ctx.request.bodyStr)
        )
    }


    /**
     * 加载属性配置
     */
    @Path(path = 'loadAttrCfg')
    ApiResp loadAttrCfg() {
        async {bean(AttrManager).init()}
        ApiResp.ok('加载中...')
    }
}

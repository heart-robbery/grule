package ctrl

import core.ServerTpl
import core.http.HttpContext
import core.http.mvc.ApiResp
import core.http.mvc.Ctrl
import core.http.mvc.Path
import core.jpa.BaseRepo
import service.rule.AttrManager
import service.rule.DecisionEngine

@Ctrl
class RuleCtrl extends ServerTpl {

    @Lazy def repo = bean(BaseRepo, 'jpa_rule_repo')
    @Lazy def engine = bean(DecisionEngine)


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
        boolean async = params['async'] == 'true' ? true : false
        ctx.render(
            ApiResp.ok(
                engine.run(decisionId, async, ctx.request.id, params)
            )
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

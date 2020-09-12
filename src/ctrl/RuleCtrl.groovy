package ctrl

import core.ServerTpl
import core.http.HttpContext
import core.http.mvc.ApiResp
import core.http.mvc.Ctrl
import core.http.mvc.Path
import core.jpa.BaseRepo
import dao.entity.Decision
import service.rule.AttrManager
import service.rule.DecisionEngine
import service.rule.DecisionManager
import service.rule.spec.DecisionSpec

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
     * 设置一条决策
     * @param id 决策数据库中的Id 如果为空, 则为新建
     * @param dsl 决策DSL
     * @return
     */
    @Path(path = 'setDecision', method = 'post')
    ApiResp setDecision(String id, String dsl) {
        DecisionSpec spec
        try {
            spec = bean(DecisionManager).create(dsl)
        } catch (ex) {
            return ApiResp.fail('语法错误')
        }
        Decision decision
        if (id) { // 更新
            decision = repo.findById(Decision, id)
            if (decision.decisionId != spec.决策id) {
                if (repo.find(Decision) {root, query, cb -> cb.equal(root.get('decisionId'), spec.决策id)}) { // 决策id 不能重
                    return ApiResp.fail("决策id($spec.决策id)已存在")
                }
            }
        } else { // 创建
            decision = new Decision()
        }
        decision.decisionId = spec.决策id
        decision.name = spec.决策名
        decision.comment = spec.决策描述
        decision.dsl = dsl
        repo.saveOrUpdate(decision)
        ApiResp.ok(decision)
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

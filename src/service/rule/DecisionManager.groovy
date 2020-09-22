package service.rule


import cn.xnatural.enet.event.EL
import core.ServerTpl
import core.jpa.BaseRepo
import service.rule.spec.DecisionSpec

import java.util.concurrent.ConcurrentHashMap

/**
 * 决策管理器
 */
class DecisionManager extends ServerTpl {
    @Lazy def repo = bean(BaseRepo, 'jpa_rule_repo')

    protected final Map<String, DecisionSpec> decisionMap = new ConcurrentHashMap<>()


    @EL(name = 'jpa_rule.started', async = true)
    void start() {
        load()
    }


    DecisionSpec findDecision(String decisionId) {
        def spec = decisionMap.get(decisionId)
        if (spec == null) {
            loadDecision(decisionId)
            spec = decisionMap.get(decisionId)
        }
        return spec
    }


    @EL(name = 'delDecision')
    void remove(String decisionId) {
        decisionMap.remove(decisionId)
        log.info("delDecision: " + decisionId)
    }
    @EL(name = 'loadDecision')
    void loadDecision(String decisionId) {
        create(repo.find(dao.entity.Decision) {root, query, cb -> cb.equal(root.get('decisionId'), decisionId)}?.dsl)
        log.info("loadDecision: " + decisionId)
    }


    /**
     * 创建策略集
     * @param s
     * @return
     */
    DecisionSpec create(String s) {
        if (s == null) return null
        def p = DecisionSpec.of(s)
        decisionMap.put(p.决策id, p)
        log.debug("添加决策: " + p.决策id)
        p
    }


    protected void load() {
        log.info("加载决策")
        if (!decisionMap.isEmpty()) decisionMap.clear()
        // create(Utils.baseDir("/src/service/rule/policy/test1.decision").getText('utf-8'))
        repo.findList(dao.entity.Decision).each { create(it.dsl) }
    }
}

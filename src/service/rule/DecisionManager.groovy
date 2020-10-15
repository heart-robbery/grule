package service.rule


import cn.xnatural.enet.event.EL
import core.ServerTpl
import core.jpa.BaseRepo
import service.rule.spec.DecisionSpec

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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
        log.debug("添加决策: {}:{}", p.决策名, p.决策id)
        p
    }


    protected void load() {
        log.info("加载决策")
        Set<String> ids = (decisionMap ? new HashSet<>() : null)
        def threshold = new AtomicInteger(1)
        def tryCompleteFn = {
            if (threshold.decrementAndGet() > 0) return
            if (ids) {
                decisionMap.collect {it.key}.each { decisionId ->
                    if (!ids.contains(decisionId)) { // 删除库里面没有, 内存里面有的数据
                        decisionMap.remove(decisionId)
                    }
                }
            }
        }
        for (int page = 0, limit = 50; ; page++) {
            def ls = repo.findList(dao.entity.Decision, page * limit, limit)
            if (!ls) { // 结束
                tryCompleteFn()
                break
            }
            threshold.incrementAndGet()
            async {
                ls.each {
                    ids?.add(it.decisionId)
                    try {
                        create(it.dsl)
                    } catch (ex) {
                        log.error("创建决策'${it.name}:${it.decisionId}'错误", ex)
                    }
                }
                tryCompleteFn()
            }
        }
    }
}

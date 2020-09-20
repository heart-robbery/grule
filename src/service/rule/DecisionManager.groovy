package service.rule

import cn.xnatural.enet.event.EC
import cn.xnatural.enet.event.EL
import core.ServerTpl
import core.Utils
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


    DecisionSpec findDecision(String decisionId) { decisionMap.get(decisionId) }


    @EL(name = 'decision.remove')
    void remove(String decisionId) {
        decisionMap.remove(decisionId)
    }


//    /**
//     * Decision 转换成 DSL
//     * @param decision
//     * @return
//     */
//    static String toDsl(dao.entity.Decision decision) {
//        StringBuilder sb = new StringBuilder()
//        sb.append("决策id = ").append(decision.decisionId).append('\n')
//        sb.append("决策名 = ").append(decision.name).append('\n')
//        if (decision.comment) sb.append("决策描述 = ").append(decision.comment).append('\n')
//        sb.append('\n')
//        decision.returnAttrs.split(';').each {attr ->
//            if (attr) sb.append("返回属性 = ").append(attr).append('\n')
//        }
//        sb.append('\n')
//        decision.policies.each {policy ->
//            sb.append("策略定义 {\n")
//            if (policy.name) sb.append("策略名 = ").append(policy.name).append('\n')
//            if (policy.comment) sb.append("策略描述 = ").append(policy.comment).append('\n')
//            sb.append('\n')
//
//            policy.rules.each {rule ->
//                sb.append("规则定义 {\n")
//
//                sb.append("}\n")
//            }
//
//            sb.append("}\n")
//        }
//        sb.toString()
//    }


    /**
     * 创建策略集
     * @param s
     * @return
     */
    DecisionSpec create(String s) {
        def p = DecisionSpec.of(s)
        decisionMap.put(p.决策id, p)
        log.debug("添加决策: " + p.决策id)
        p
    }


    protected void load() {
        log.info("加载决策")
        decisionMap.clear()
        // create(Utils.baseDir("/src/service/rule/policy/test1.decision").getText('utf-8'))
        repo.findList(dao.entity.Decision).each { create(it.dsl) }
    }
}

package service.rule

import cn.xnatural.enet.event.EL
import core.Utils
import core.module.ServerTpl
import service.rule.spec.DecisionSpec

import java.util.concurrent.ConcurrentHashMap

/**
 * 决策管理器
 */
class DecisionManager extends ServerTpl {

    protected final Map<String, DecisionSpec> decisionMap = new ConcurrentHashMap<>()


    @EL(name = 'sys.starting')
    void start() {
        load()
    }


    DecisionSpec findDecision(String id) { decisionMap.get(id) }


    /**
     * 创建策略集
     * @param s
     * @return
     */
    DecisionSpec create(String s) {
        def p = DecisionSpec.of(s)
        decisionMap.put(p.决策id, p)
        p
    }


    protected void load() {
        log.info("加载决策")
        Utils.baseDir("/src/service/rule/policy/").eachFileRecurse {f ->
            if (f.name.endsWith(".decision")) {
                create(f.getText('utf-8'))
            }
        }
    }
}

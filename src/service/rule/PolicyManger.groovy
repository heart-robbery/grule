package service.rule

import cn.xnatural.enet.event.EL
import core.Utils
import core.module.ServerTpl
import service.rule.spec.PolicySpec

import java.util.concurrent.ConcurrentHashMap

class PolicyManger extends ServerTpl {

    protected final Map<String, PolicySpec> policyMap = new ConcurrentHashMap<>()


    @EL(name = 'sys.starting', async = true)
    void start() {
        load()
    }


    PolicySpec findPolicy(String name) { policyMap.get(name) }


    /**
     * 创建策略
     * @param s
     * @return
     */
    PolicySpec create(String s) {
        def p = PolicySpec.of(s)
        policyMap.put(p.策略名, p)
        p
    }


    protected void load() {
        log.info("加载策略")
        Utils.baseDir("/src/service/rule/policy/").eachFileRecurse {f ->
            if (f.name.endsWith(".policy")) {
                create(f.getText('utf-8'))
            }
        }
    }
}

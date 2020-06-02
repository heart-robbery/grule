package sevice.rule

import cn.xnatural.enet.event.EL
import core.Utils
import core.module.ServerTpl
import sevice.rule.spec.PolicySetSpec

import java.util.concurrent.ConcurrentHashMap

class PolicySetManager extends ServerTpl {

    protected final Map<String, PolicySetSpec> policySetMap = new ConcurrentHashMap<>()


    @EL(name = 'sys.starting')
    void start() {
        load()
    }


    PolicySetSpec findPolicySet(String id) { policySetMap.get(id) }


    /**
     * 创建策略集
     * @param s
     * @return
     */
    PolicySetSpec create(String s) {
        def p = PolicySetSpec.of(s)
        policySetMap.put(p.策略集id, p)
        p
    }


    protected void load() {
        log.info("加载策略集")
        Utils.baseDir("/src/sevice/rule/policy/").eachFileRecurse {f ->
            if (f.name.endsWith(".policySet")) {
                create(f.getText('utf-8'))
            }
        }
    }
}

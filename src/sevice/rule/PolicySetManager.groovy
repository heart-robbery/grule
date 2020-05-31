package sevice.rule

import cn.xnatural.enet.event.EL
import core.Utils
import core.module.ServerTpl
import sevice.rule.spec.PolicySetSpec

import java.util.concurrent.ConcurrentHashMap

class PolicySetManager extends ServerTpl {

    protected final Map<String, PolicySetSpec> policySetMap = new ConcurrentHashMap<>()


    @EL(name = 'sys.started')
    void started() {
        load()
    }


    PolicySetSpec findPolicySet(String id) { policySetMap.get(id) }


    protected void load() {
        log.info("加载策略集")
        Utils.baseDir("/src/sevice/rule/policy/").eachFileRecurse {f ->
            if (f.name.endsWith(".policySet")) {
                def p = PolicySetSpec.of(f.getText('utf-8'))
                policySetMap.put(p.策略集id, p)
            }
        }
    }
}

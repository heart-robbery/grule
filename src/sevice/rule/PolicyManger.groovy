package sevice.rule

import cn.xnatural.enet.event.EL
import core.Utils
import core.module.ServerTpl
import sevice.rule.spec.PolicySpec

import java.util.concurrent.ConcurrentHashMap

class PolicyManger extends ServerTpl {

    protected final Map<String, PolicySpec> policyMap = new ConcurrentHashMap<>()


    @EL(name = 'sys.started')
    void started() {
        load()
    }


    PolicySpec findPolicy(String name) { policyMap.get(name) }


    protected void load() {
        log.info("加载策略")
        Utils.baseDir("/src/sevice/rule/policy/").eachFileRecurse {f ->
            if (f.name.endsWith(".policy")) {
                def p = PolicySpec.of(f.getText('utf-8'))
                policyMap.put(p.策略名, p)
            }
        }
    }
}

package sevice.rule

import core.module.ServerTpl
import sevice.rule.spec.PolicySpec

import java.util.concurrent.ConcurrentHashMap

class PolicyManger extends ServerTpl {

    Map<String, PolicySpec> policyMap = new ConcurrentHashMap<>()

    PolicySpec findPolicy(String name) {
        policyMap.computeIfAbsent(name, {
            PolicySpec.of(new File("D:\\code_repo\\gy\\src\\sevice\\rule\\policy\\${name}.policy").getText('utf-8'))
        })
    }
}

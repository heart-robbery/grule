package sevice.rule

import core.module.ServerTpl
import sevice.rule.spec.PolicySetSpec

class PolicySetManager extends ServerTpl {

    PolicySetSpec findPolicySet(String name ) {
        PolicySetSpec.of(new File("D:\\code_repo\\gy\\src\\sevice\\rule\\policy\\${name}.policySet").getText('utf-8'))
    }
}

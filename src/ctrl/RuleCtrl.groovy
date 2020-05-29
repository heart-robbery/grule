package ctrl

import ratpack.handling.RequestId
import sevice.rule.RuleEngine

class RuleCtrl extends CtrlTpl {

    @Lazy def re = bean(RuleEngine)


    @Path(method = 'post', path = 'risk', consume = 'application/json')
    def risk(Map params, RequestId reqId) {
        String pn = params['policySet']?:'test'
        if (!pn) throw new IllegalArgumentException("policySet must not be empty")
        boolean async = params['async'] == 'true' ? true : false
        re.run(pn, async, reqId.toString(), params)
    }
}

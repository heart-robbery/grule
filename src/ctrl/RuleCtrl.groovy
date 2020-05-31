package ctrl

import ratpack.handling.RequestId
import sevice.rule.RuleEngine

import java.util.function.Consumer

class RuleCtrl extends CtrlTpl {

    @Lazy def re = bean(RuleEngine)


    @Path(method = 'get', path = 'risk')
    void risk(Map params, RequestId reqId, Consumer cb) {
        String pn = params['policySet']?:'test_ps1'
        if (!pn) throw new IllegalArgumentException("policySet must not be empty")
        boolean async = params['async'] == 'true' ? true : false
        re.run(pn, async, reqId.toString(), params, cb)
    }
}

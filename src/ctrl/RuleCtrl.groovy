package ctrl

import ctrl.common.ApiResp
import ratpack.exec.Promise
import ratpack.handling.Chain
import ratpack.handling.RequestId
import sevice.rule.PolicyManger
import sevice.rule.PolicySetManager
import sevice.rule.RuleEngine

import java.nio.charset.Charset

class RuleCtrl extends CtrlTpl {

    @Lazy def re = bean(RuleEngine)


    void risk(Chain chain) {
        chain.path('risk') {ctx ->
            ctx.render Promise.async{ down ->
                async {
                    try {
                        String pn = ctx.request.queryParams['policySet']?:'test_ps1'
                        if (!pn) throw new IllegalArgumentException("policySet must not be empty")
                        boolean async = ctx.request.queryParams['async'] == 'true' ? true : false
                        down.success(ApiResp.ok(
                            re.run(pn, async, ctx.get(RequestId.TYPE).toString(), ctx.request.queryParams)
                        ))
                    } catch (Exception ex) {
                        down.success(ApiResp.fail(ex.message?:ex.class.simpleName))
                        log.info("", ex)
                    }
                }
            }
        }
    }

    void setPolicy(Chain chain) {
        chain.post('setPolicy') {ctx ->
            ctx.request.body.then {data ->
                ctx.render Promise.async{ down ->
                    async {
                        try {
                            def p = bean(PolicyManger).create(data.getText(Charset.forName('utf-8')))
                            down.success(ApiResp.ok(p))
                        } catch (Exception ex) {
                            down.success(ApiResp.fail(ex.message?:ex.class.simpleName))
                            log.info("", ex)
                        }
                    }
                }
            }
        }
    }

    void setPolicySet(Chain chain) {
        chain.post('setPolicySet') {ctx ->
            ctx.request.body.then {data ->
                ctx.render Promise.async{ down ->
                    async {
                        try {
                            def p = bean(PolicySetManager).create(data.getText(Charset.forName('utf-8')))
                            down.success(ApiResp.ok(p))
                        } catch (Exception ex) {
                            down.success(ApiResp.fail(ex.message?:ex.class.simpleName))
                            log.info("", ex)
                        }
                    }
                }
            }
        }
    }
}

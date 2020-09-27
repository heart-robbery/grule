package ctrl

import core.ServerTpl
import core.http.HttpContext
import core.http.mvc.ApiResp
import core.http.mvc.Ctrl
import core.http.mvc.Filter
import core.jpa.BaseRepo
import service.rule.UserSrv

@Ctrl(prefix = 'mnt/user')
class MntUserCtrl extends ServerTpl {
    @Lazy def repo = bean(BaseRepo, 'jpa_rule_repo')
    @Lazy def userSrv = bean(UserSrv)


    @Filter
    void filter(HttpContext ctx) {
        def name = ctx.getSessionAttr('name')
        if (!name) {
            ctx.response.status(401)
            ApiResp.fail('用户会话已失效, 请重新登录')
        }
    }
}

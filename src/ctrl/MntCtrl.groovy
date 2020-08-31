package ctrl

import core.ServerTpl
import core.http.HttpContext
import core.http.mvc.ApiResp
import core.http.mvc.Ctrl
import core.http.mvc.Path

@Ctrl(prefix = 'mnt')
class MntCtrl extends ServerTpl {


    /**
     * 登录
     * @param username
     * @param password
     * @param ctx
     * @return
     */
    @Path(path = 'login')
    ApiResp login(String username, String password, HttpContext ctx) {
        ctx.setSessionAttr('name', username)
        ApiResp.ok().attr('name', username)
    }


    /**
     * 获取当前 会话 中的用户信息
     * @param ctx
     * @return
     */
    @Path(path = 'getCurrentUser')
    ApiResp getCurrentUser(HttpContext ctx) {
        String name = ctx.getSessionAttr('name')
        if (name) {
            ApiResp.ok().attr('name', name)
        } else {
            ctx.response.status(401)
            ApiResp.fail('用户会话已失效, 请重新登录')
        }
    }
}

package ctrl


import cn.xnatural.enet.event.EL
import core.ServerTpl
import core.http.HttpContext
import core.http.mvc.ApiResp
import core.http.mvc.Ctrl
import core.http.mvc.Filter
import core.http.mvc.Path
import core.http.ws.Listener
import core.http.ws.WS
import core.http.ws.WebSocket
import core.jpa.BaseRepo
import dao.entity.User

import java.util.concurrent.ConcurrentHashMap


@Ctrl(prefix = 'mnt')
class MntCtrl extends ServerTpl {

    @Lazy def repo = bean(BaseRepo, 'jpa_rule_repo')
    protected final Set<WebSocket> wss = ConcurrentHashMap.newKeySet()


    @Filter
    void filter(HttpContext ctx) {
        if (ctx.pieces?[0] !in ['login']) { // session 判断, login 不拦截
            def res = getCurrentUser(ctx)
            if (res.code != '00') { // 判断当前session 是否过期
                ctx.render(res)
            }
        }
    }


    @EL(name = 'wsMsg_rule')
    void wsMsgBroadcast(String msg) { wss.each {ws -> ws.send(msg)} }

    @WS(path = 'ws')
    void receiveWs(WebSocket ws) {
        log.info('WS connect. {}', ws.session.sc.remoteAddress)
        ws.listen(new Listener() {

            @Override
            void onClose(WebSocket wst) {
                wss.remove(wst)
            }

            @Override
            void onText(String msg) {
                log.info('rule mnt ws receive client msg: {}', msg)
            }
        })
        wss.add(ws)
    }


    /**
     * 登录
     * @param username
     * @param password
     * @param ctx
     * @return
     */
    @Path(path = 'login')
    ApiResp login(String username, String password, HttpContext ctx) {
        if (!username) return ApiResp.fail('username must not be empty')
        if (!password) return ApiResp.fail('password must not be empty')
        def user = repo.find(User) {root, query, cb -> cb.equal(root.get('name'), username)}
        if (!user) return ApiResp.fail("用户不存在")
        if (password != user.password) return ApiResp.fail('密码错误')
        ctx.setSessionAttr('id', user.id)
        ctx.setSessionAttr('name', username)
        ctx.setSessionAttr('permissions', user.permissions?.split(",") as Set)
        user.login = new Date()
        repo.saveOrUpdate(user)
        ApiResp.ok().attr('id', user.id).attr('name', username)
            .attr('permissions', user.permissions?.split(","))
    }


    @Path(path = 'logout')
    ApiResp logout(HttpContext ctx) {
        ctx.setSessionAttr('id', null)
        ctx.setSessionAttr('name', null)
        ApiResp.ok()
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
            ApiResp.ok().attr('id', ctx.getSessionAttr('id')).attr('name', name)
                .attr('permissions', ctx.getSessionAttr("permissions", Set))
        } else {
            ctx.response.status(401)
            ApiResp.fail('用户会话已失效, 请重新登录')
        }
    }
}

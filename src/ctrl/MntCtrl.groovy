package ctrl

import cn.xnatural.app.ServerTpl
import cn.xnatural.enet.event.EL
import cn.xnatural.http.*
import cn.xnatural.jpa.Repo
import entity.User

import java.util.concurrent.ConcurrentHashMap

@Ctrl(prefix = 'mnt')
class MntCtrl extends ServerTpl {

    @Lazy def repo = bean(Repo, 'jpa_rule_repo')
    protected final Set<WebSocket> wss = ConcurrentHashMap.newKeySet()


    @EL(name = 'wsMsg_rule')
    void wsMsgBroadcast(String msg) { wss.each {ws -> ws.send(msg)} }

    @WS(path = 'ws')
    void receiveWs(WebSocket ws) {
        log.info('WS connect. {}', ws.session.getRemoteAddress())
        ws.listen(new WsListener() {

            @Override
            void onClose(WebSocket wst) { wss.remove(wst) }

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
     * @param hCtx
     * @return
     */
    @Path(path = 'login')
    ApiResp login(String username, String password, HttpContext hCtx) {
        if (!username) return ApiResp.fail('Param username not empty')
        if (!password) return ApiResp.fail('Param password not empty')
        def user = repo.find(User) {root, query, cb -> cb.equal(root.get('name'), username)}
        if (!user) return ApiResp.fail("用户不存在")
        hCtx.setSessionAttr('permissions', user.permissions)
        hCtx.auth("mnt-login")
        if (password != user.password) return ApiResp.fail('密码错误')

        hCtx.setSessionAttr('uId', user.id)
        hCtx.setSessionAttr('uName', username)
        hCtx.setSessionAttr('uGroup', user.group)
        user.login = new Date()
        repo.saveOrUpdate(user)
        ApiResp.ok().attr('id', user.id).attr('name', username)
            .attr('permissionIds', user.permissions?.split(",")?:[])
    }


    /**
     * 退出会话
     * @param ctx
     * @return
     */
    @Path(path = 'logout')
    ApiResp logout(HttpContext ctx) {
        ctx.setSessionAttr('uId', null)
        ctx.setSessionAttr('uName', null)
        ApiResp.ok()
    }
}

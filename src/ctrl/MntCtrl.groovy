package ctrl

import cn.xnatural.enet.event.EL
import core.ServerTpl
import core.http.HttpContext
import core.http.mvc.ApiResp
import core.http.mvc.Ctrl
import core.http.mvc.Path
import core.http.ws.Listener
import core.http.ws.WS
import core.http.ws.WebSocket
import core.jpa.BaseRepo
import dao.entity.Decision
import dao.entity.RuleField

import java.util.concurrent.ConcurrentHashMap

@Ctrl(prefix = 'mnt')
class MntCtrl extends ServerTpl {

    @Lazy def repo = bean(BaseRepo, 'jpa_rule_repo')

    protected final Set<WebSocket> wss = ConcurrentHashMap.newKeySet()


    @EL(name = 'wsMsg')
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
        ctx.setSessionAttr('name', username)
        ctx.setSessionAttr('id', username)
        ctx.setSessionAttr('uRoles', ['admin'] as Set)
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


    @Path(path = 'decisionPage')
    ApiResp decisionPage(Integer page, String kw, String decisionId) {
        ApiResp.ok(
            repo.findPage(Decision, page, 10) {root, query, cb ->
                query.orderBy(cb.desc(root.get('updateTime')))
                if (kw) cb.like(root.get('dsl'), '%' + kw + '%')
                else if (decisionId) cb.equal(root.get('decisionId'), decisionId)
            }
        )
    }


    @Path(path = 'fieldPage')
    ApiResp fieldPage(Integer page, String kw) {
        ApiResp.ok(
            repo.findPage(RuleField, page, 10) { root, query, cb ->
                query.orderBy(cb.desc(root.get('updateTime')))
                if (kw) {
                    cb.or(
                        cb.like(root.get('enName'), '%' + kw + '%'),
                        cb.like(root.get('cnName'), '%' + kw + '%'),
                        cb.like(root.get('comment'), '%' + kw + '%')
                    )
                }
            }
        )
    }


    @Path(path = 'delDecision/:decisionId')
    ApiResp delDecision(String decisionId) {
        repo.delete(repo.find(Decision) {root, query, cb -> cb.equal(root.get('decisionId'), decisionId)})
        ApiResp.ok()
    }


    @Path(path = 'delField/:enName')
    ApiResp delField(String enName) {
        repo.delete(repo.find(RuleField) {root, query, cb -> cb.equal(root.get('enName'), enName)})
        ApiResp.ok()
    }


//    @Path(path = 'decisionDetail/:decisionId')
//    ApiResp decisionDetail(String decisionId) {
//        if (!decisionId) return ApiResp.fail("decisionId must not be empty")
//        def decision = repo.find(Decision) {root, query, cb ->
//            cb.equal(root.get('decisionId'), decisionId)
//        }
//        // def attr = Utils.toMapper(decision).ignore('policies').build()
//        // attr.put('dsl', DecisionManager.toDsl(decision))
//        ApiResp.ok(decision)
//    }


//    @Path(path = 'selectDecision')
//    ApiResp selectDecision(String nameLike) {
//        ApiResp.ok(
//            repo.findList(Decision) {root, query, cb ->
//                if (nameLike) cb.like(root.get('name'), '%' + nameLike + '%')
//                else query.orderBy(cb.desc(root.get('createTime')))
//            }.collectEntries {[it.decisionId, it.name]}
//        )
//    }
}

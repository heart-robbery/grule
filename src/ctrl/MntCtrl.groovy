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
import dao.entity.DataCollector
import dao.entity.Decision
import dao.entity.FieldType
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
    ApiResp decisionPage(Integer page, String kw) {
        ApiResp.ok(
            repo.findPage(Decision, page, 10) {root, query, cb ->
                query.orderBy(cb.desc(root.get('updateTime')))
                if (kw) cb.like(root.get('dsl'), '%' + kw + '%')
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


    @Path(path = 'dataCollectorPage')
    ApiResp dataCollectorPage(Integer page, Integer pageSize, String kw) {
        if (pageSize && pageSize > 20) return ApiResp.fail("pageSize max 20")
        ApiResp.ok(
            repo.findPage(DataCollector, page, pageSize?:10) { root, query, cb ->
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


    @Path(path = 'addField', method = 'post')
    ApiResp addField(String enName, String cnName, FieldType type, String comment, String dataCollector) {
        if (!enName) return ApiResp.fail("enName must not be empty")
        if (!cnName) return ApiResp.fail("cnName must not be empty")
        if (!type) return ApiResp.fail("type must not be empty")
        if (repo.count(RuleField) {root, query, cb -> cb.equal(root.get('enName'), enName)}) return ApiResp.fail("$enName aleady exist")
        if (repo.count(RuleField) {root, query, cb -> cb.equal(root.get('cnName'), cnName)}) return ApiResp.fail("$cnName aleady exist")
        def field = new RuleField(enName: enName, cnName: cnName, type: type, comment: comment, dataCollector: dataCollector)
        repo.saveOrUpdate(field)
        ApiResp.ok(field)
    }


    @Path(path = 'addDataCollector', method = 'post')
    ApiResp addDataCollector(HttpContext ctx) {
        def map = ctx.params()
        DataCollector dc = new DataCollector()
        map.each {entry -> if (dc.hasProperty(entry.key)) dc.setProperty(entry.key, entry.value)}
        if (!dc.enName) return ApiResp.fail('enName must not be empty')
        if (!dc.cnName) return ApiResp.fail('cnName must not be empty')
        if (!dc.type) return ApiResp.fail('type must not be empty')
        if ('http' == dc.type) {
            if (!dc.url) return ApiResp.fail('url must not be empty')
            if (!dc.method) return ApiResp.fail('method must not be empty')
            if (!dc.contentType) return ApiResp.fail('contentType must not be empty')
        } else if ('script' == dc.type) {
            if (!dc.computeScript) return ApiResp.fail('computeScript must not be empty')
        }
        if (repo.find(DataCollector) {root, query, cb -> cb.equal(root.get('enName'), dc.enName)}) {
            return ApiResp.fail("$dc.enName 已存在")
        }
        if (repo.find(DataCollector) {root, query, cb -> cb.equal(root.get('cnName'), dc.cnName)}) {
            return ApiResp.fail("$dc.cnName 已存在")
        }
        repo.saveOrUpdate(dc)
        ApiResp.ok(dc)
    }


    @Path(path = 'updateField', method = 'post')
    ApiResp updateField(Long id, String enName, String cnName, FieldType type, String comment, String dataCollector) {
        if (!id) return ApiResp.fail("id not legal")
        if (!enName) return ApiResp.fail("enName must not be empty")
        if (!cnName) return ApiResp.fail("cnName must not be empty")
        if (!type) return ApiResp.fail("type must not be empty")
        def field = repo.findById(RuleField, id)
        if (field == null) return ApiResp.fail("id: $id not found")
        if (enName != field.enName && repo.count(RuleField) {root, query, cb -> cb.equal(root.get('enName'), enName)}) {
            return ApiResp.fail("$enName aleady exist")
        }
        if (cnName != field.cnName && repo.count(RuleField) {root, query, cb -> cb.equal(root.get('cnName'), cnName)}) {
            return ApiResp.fail("$cnName aleady exist")
        }

        field.enName = enName
        field.cnName = cnName
        field.type = type
        field.comment = comment
        field.dataCollector = dataCollector

        repo.saveOrUpdate(field)
        ep.fire('updateField', field)
        ApiResp.ok(field)
    }


    @Path(path = 'updateDataCollector', method = 'post')
    ApiResp updateDataCollector(Long id, String enName, String cnName, String url, String bodyStr, String method, String parseScript, String contentType, String comment, String computeScript) {
        if (!id) return ApiResp.fail("id not legal")
        if (!enName) return ApiResp.fail("enName must not be empty")
        if (!cnName) return ApiResp.fail("cnName must not be empty")
        def collector = repo.findById(DataCollector, id)
        if (collector == null) return ApiResp.fail("id: $id not found")
        if ('http' == collector.type) {
            if (!url) return ApiResp.fail('url must not be empty')
            if (!method) return ApiResp.fail('method must not be empty')
            if (!contentType) return ApiResp.fail('contentType must not be empty')
            collector.url = url
            collector.method = method
            collector.contentType = contentType
            collector.bodyStr = bodyStr
            collector.parseScript = parseScript
        } else if ('script' == collector.type) {
            if (!computeScript) return ApiResp.fail('computeScript must not be empty')
            collector.computeScript = computeScript
        }
        if (enName != collector.enName ) {
            if (repo.count(DataCollector) {root, query, cb -> cb.equal(root.get('enName'), enName)}) {
                return ApiResp.fail("$enName aleady exist")
            }
            collector.enName = enName
        }
        if (cnName != collector.cnName) {
            if (repo.count(DataCollector) {root, query, cb -> cb.equal(root.get('cnName'), cnName)}) {
                return ApiResp.fail("$cnName aleady exist")
            }
            collector.cnName = cnName
        }

        collector.comment = comment

        repo.saveOrUpdate(collector)
        ep.fire('updateDataCollector', collector)
        ApiResp.ok(collector)
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


    @Path(path = 'delDataCollector/:enName')
    ApiResp delDataCollector(String enName) {
        repo.delete(repo.find(DataCollector) {root, query, cb -> cb.equal(root.get('enName'), enName)})
        ApiResp.ok()
    }
}

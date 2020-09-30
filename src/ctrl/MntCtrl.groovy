package ctrl

import cn.xnatural.enet.event.EC
import cn.xnatural.enet.event.EL
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import core.Page
import core.ServerTpl
import core.Utils
import core.http.HttpContext
import core.http.mvc.ApiResp
import core.http.mvc.Ctrl
import core.http.mvc.Filter
import core.http.mvc.Path
import core.http.ws.Listener
import core.http.ws.WS
import core.http.ws.WebSocket
import core.jpa.BaseRepo
import dao.entity.*
import org.hibernate.query.internal.NativeQueryImpl
import org.hibernate.transform.Transformers
import service.rule.AttrManager
import service.rule.DecisionManager
import service.rule.spec.DecisionSpec

import javax.persistence.criteria.Predicate
import java.text.SimpleDateFormat
import java.util.Map.Entry
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


    @Path(path = 'decisionPage')
    ApiResp decisionPage(Integer page, Integer pageSize, String kw, String nameLike, String decisionId) {
        if (pageSize && pageSize > 20) return ApiResp.fail("pageSize max 20")
        ApiResp.ok(
            repo.findPage(Decision, page, pageSize?:10) {root, query, cb ->
                query.orderBy(cb.desc(root.get('updateTime')))
                if (decisionId) return cb.equal(root.get('decisionId'), decisionId)
                if (nameLike) return cb.like(root.get('name'), '%' + nameLike + '%')
                if (kw) return cb.like(root.get('dsl'), '%' + kw + '%')
            }
        )
    }


    @Path(path = 'fieldPage')
    ApiResp fieldPage(Integer page, String kw) {
        def fieldPage = Page.of(repo.findPage(RuleField, page, 10) { root, query, cb ->
            query.orderBy(cb.desc(root.get('updateTime')))
            if (kw) {
                cb.or(
                    cb.like(root.get('enName'), '%' + kw + '%'),
                    cb.like(root.get('cnName'), '%' + kw + '%'),
                    cb.like(root.get('comment'), '%' + kw + '%')
                )
            }
        }, { Utils.toMapper(it).build()})
        def collectorNames = fieldPage.list.collect {it.dataCollector}.findAll{it}
        if (collectorNames) {
            repo.findList(DataCollector) {root, query, cb -> root.get('enName').in(collectorNames)}.each {dc ->
                fieldPage.list.find {it.dataCollector == dc.enName}?.dataCollectorName = dc.cnName
            }
        }
        ApiResp.ok(fieldPage)
    }


    @Path(path = 'dataCollectorPage')
    ApiResp dataCollectorPage(Integer page, Integer pageSize, String kw, String enName) {
        if (pageSize && pageSize > 50) return ApiResp.fail("pageSize max 50")
        ApiResp.ok(
            repo.findPage(DataCollector, page, pageSize?:10) { root, query, cb ->
                query.orderBy(cb.desc(root.get('updateTime')))
                def ps = []
                if (kw) {
                    ps << cb.or(
                        cb.like(root.get('enName'), '%' + kw + '%'),
                        cb.like(root.get('cnName'), '%' + kw + '%'),
                        cb.like(root.get('comment'), '%' + kw + '%')
                    )
                }
                if (enName) {
                    ps << cb.equal(root.get('enName'), enName)
                }
                cb.and(ps.toArray(new Predicate[ps.size()]))
            }
        )
    }


    @Path(path = 'opHistoryPage')
    ApiResp opHistoryPage(Integer page, Integer pageSize, String kw, String type) {
        if (pageSize && pageSize > 20) return ApiResp.fail("pageSize max 20")
        ApiResp.ok(
            repo.findPage(OpHistory, page, pageSize?:10) { root, query, cb ->
                query.orderBy(cb.desc(root.get('createTime')))
                def ps = []
                if (kw) {
                    ps << cb.like(root.get('content'), '%' + kw + '%')
                }
                if (type) {
                    ps << cb.equal(root.get('tbName'), repo.tbName(Decision.package.name + "." + type))
                }
                cb.and(ps.toArray(new Predicate[ps.size()]))
            }
        )
    }


    @Path(path = 'decisionResultPage')
    ApiResp decisionResultPage(
        Integer page, Integer pageSize, String id, String decisionId, service.rule.Decision decision,
        String idNum, Long spend, String exception, String input, String attrs, String rules
    ) {
        if (pageSize && pageSize > 10) return ApiResp.fail("pageSize max 10")
        ApiResp.ok(
            Page.of(
                repo.findPage(DecisionResult, page, pageSize?:10) { root, query, cb ->
                    query.orderBy(cb.desc(root.get('occurTime')))
                    def ps = []
                    if (id) ps << cb.equal(root.get('id'), id)
                    if (decisionId) ps << cb.equal(root.get('decisionId'), decisionId)
                    if (idNum) ps << cb.equal(root.get('idNum'), idNum)
                    if (spend) ps << cb.ge(root.get('spend'), spend)
                    if (decision) ps << cb.equal(root.get('decision'), decision)
                    if (exception) ps << cb.like(root.get('exception'), '%' + exception + '%')
                    if (input) ps << cb.like(root.get('input'), '%' + input + '%')
                    if (attrs) ps << cb.like(root.get('attrs'), '%' + attrs + '%')
                    if (rules) ps << cb.like(root.get('rules'), '%' + rules + '%')
                    if (ps) cb.and(ps.toArray(new Predicate[ps.size()]))
                },
                {
                    def am = bean(AttrManager)
                    Utils.toMapper(it).addConverter('decisionId', 'decisionName', {String dId ->
                        bean(DecisionManager).findDecision(dId).决策名
                    }).addConverter('attrs', {
                        it == null ? null : JSON.parseObject(it).collect { e ->
                            [enName: e.key, cnName: am.attrMap.get(e.key)?.cnName, value: e.value]
                        }
                    }).addConverter('input', {
                        it == null ? null : JSON.parseObject(it)
                    }).addConverter('dataCollectResult', {
                        it == null ? null : JSON.parseObject(it)
                    }).addConverter('rules', {
                        def arr = it == null ? null : JSON.parseArray(it)
                        arr?.each { JSONObject jo ->
                            jo.put('data', jo.getJSONObject('data').collect { Entry<String, Object> e ->
                                [enName: e.key, cnName: am.attrMap.get(e.key)?.cnName, value: e.value]
                            })
                        }
                        arr
                    }).build()
                }
            )
        )
    }


    @Path(path = 'collectResultPage')
    ApiResp collectResultPage(
        Integer page, Integer pageSize, String decideId, String collectorType, String collector, String decisionId,
        Long spend, Boolean success, String startTime, String endTime
    ) {
        Date start = startTime ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(startTime) : null
        Date end = endTime ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(endTime) : null
        if (pageSize && pageSize > 50) return ApiResp.fail("pageSize max 50")
        def result = Page.of(
            repo.findPage(CollectResult, page, pageSize?:10) { root, query, cb ->
                query.orderBy(cb.desc(root.get('collectDate')))
                def ps = []
                if (decideId) ps << cb.equal(root.get('decideId'), decideId)
                if (decisionId) ps << cb.equal(root.get('decisionId'), decisionId)
                if (collectorType) ps << cb.equal(root.get('collectorType'), collectorType)
                if (collector) ps << cb.equal(root.get('collector'), collector)
                if (spend) ps << cb.ge(root.get('spend'), spend)
                if (start) ps << cb.greaterThanOrEqualTo(root.get('collectDate'), start)
                if (end) ps << cb.lessThanOrEqualTo(root.get('collectDate'), end)
                if (success != null) {
                    if (success) {
                        ps << cb.equal(root.get('status'), '0000')
                    } else {
                        ps << cb.notEqual(root.get('status'), '0000')
                    }
                }
                if (ps) cb.and(ps.toArray(new Predicate[ps.size()]))
            },
            {record ->
                def m = Utils.toMapper(record).addConverter('decisionId', 'decisionName', {String dId ->
                    bean(DecisionManager).findDecision(dId).决策名
                }).build()
                m.put('success', record.httpException == null && record.parseException == null && record.scriptException == null)
                m
            }
        )
        repo.findList(DataCollector) {root, query, cb -> root.get('enName').in(result.list.collect {it['collector']})}.each {dc ->
            for (def m : result.list) {
                if (m['collector'] == dc.enName) {
                    m['collectorName'] = dc.cnName
                }
            }
        }
        ApiResp.ok(result)
    }


    /**
     * 设置一条决策
     * @param id 决策数据库中的Id 如果为空, 则为新建
     * @param dsl 决策DSL
     * @return
     */
    @Path(path = 'setDecision', method = 'post')
    ApiResp setDecision(String id, String dsl, HttpContext ctx) {
        DecisionSpec spec
        try {
            spec = bean(DecisionManager).create(dsl)
        } catch (ex) {
            log.error("语法错误", ex)
            return ApiResp.fail('语法错误: ' + ex.message)
        }

        for (def policy : spec.policies) {
            for (def rule : policy.rules) {
                if (!rule.规则名) return ApiResp.fail('规则名字不能为空')
            }
        }

        Decision decision
        def removeFn
        if (id) { // 更新
            ctx.auth('decision-update')
            decision = repo.findById(Decision, id)
            if (decision.decisionId != spec.决策id) {
                if (repo.find(Decision) {root, query, cb -> cb.equal(root.get('decisionId'), spec.决策id)}) { // 决策id 不能重
                    return ApiResp.fail("决策id($spec.决策id)已存在")
                }
                removeFn = {
                    def dId = decision.decisionId
                    bean(DecisionManager).remove(dId)
                    bean(DecisionManager).loadDecision(spec.决策id)
                    ep.fire('remote', EC.of(this).attr('toAll', true).args(app.name, 'delDecision', [dId]))
                    ep.fire('remote', EC.of(this).attr('toAll', true).args(app.name, 'loadDecision', [spec.决策id]))
                }
            }
        } else { // 创建
            ctx.auth('decision-add')
            decision = new Decision()
        }
        decision.decisionId = spec.决策id
        decision.name = spec.决策名
        decision.comment = spec.决策描述
        decision.dsl = dsl
        repo.saveOrUpdate(decision)
        if (removeFn) removeFn()
        if (!id) {
            bean(DecisionManager).loadDecision(decision.decisionId)
            ep.fire('remote', EC.of(this).attr('toAll', true).args(app.name, 'loadDecision', [decision.decisionId]))
        }
        ep.fire('enHistory', decision, ctx.getSessionAttr('name'))
        ApiResp.ok(decision)
    }


    @Path(path = 'addField', method = 'post')
    ApiResp addField(HttpContext ctx, String enName, String cnName, FieldType type, String comment, String dataCollector) {
        ctx.auth('field-add')
        if (!enName) return ApiResp.fail("enName must not be empty")
        if (!cnName) return ApiResp.fail("cnName must not be empty")
        if (!type) return ApiResp.fail("type must not be empty")
        if (repo.count(RuleField) {root, query, cb -> cb.equal(root.get('enName'), enName)}) return ApiResp.fail("$enName aleady exist")
        if (repo.count(RuleField) {root, query, cb -> cb.equal(root.get('cnName'), cnName)}) return ApiResp.fail("$cnName aleady exist")
        def field = new RuleField(enName: enName, cnName: cnName, type: type, comment: comment, dataCollector: dataCollector)
        repo.saveOrUpdate(field)
        ep.fire('addField', field.enName)
        ep.fire('remote', EC.of(this).attr('toAll', true).args(app.name, 'addField', [enName]))
        ep.fire('enHistory', field, ctx.getSessionAttr('name'))
        ApiResp.ok(field)
    }


    @Path(path = 'addDataCollector', method = 'post')
    ApiResp addDataCollector(
        HttpContext ctx, String enName, String cnName, String type, String url, String bodyStr,
        String method, String parseScript, String contentType, String comment, String computeScript,
        String sqlScript, Integer minIdle, Integer maxActive, Integer timeout
    ) {
        ctx.auth('dataCollector-add')
        DataCollector collector = new DataCollector()
        collector.enName = enName
        collector.cnName = cnName
        collector.type = type
        collector.comment = comment
        if (!collector.enName) return ApiResp.fail('enName must not be empty')
        if (!collector.cnName) return ApiResp.fail('cnName must not be empty')
        if (!collector.type) return ApiResp.fail('type must not be empty')
        if ('http' == collector.type) {
            if (!url) return ApiResp.fail('url must not be empty')
            if (!method) return ApiResp.fail('method must not be empty')
            if (!contentType) return ApiResp.fail('contentType must not be empty')
            if (!url.startsWith("http")) return ApiResp.fail('url incorrect')
            collector.parseScript = parseScript?.trim()
            if (collector.parseScript && !collector.parseScript.startsWith("{") && !collector.parseScript.endsWith("}")) {
                return ApiResp.fail('parseScript is not a function, must startWith {, endWith }')
            }
            collector.url = url
            collector.method = method
            collector.bodyStr = bodyStr
            collector.contentType = contentType
            collector.timeout = timeout
        } else if ('script' == collector.type) {
            if (!computeScript) return ApiResp.fail('computeScript must not be empty')
            collector.computeScript = computeScript
        } else if ('sql' == collector.type) {
            if (!sqlScript) return ApiResp.fail('sqlScript must not be empty')
            if (minIdle < 0 || collector.minIdle > 50) return ApiResp.fail('0 <= minIdle <= 50')
            if (maxActive < 1 || collector.maxActive > 100) return ApiResp.fail('1 <= minIdle <= 100')
            collector.sqlScript = sqlScript
            collector.minIdle = minIdle
            collector.maxActive = maxActive
        } else return ApiResp.fail('Not support type: ' + collector.type)
        if (repo.find(DataCollector) {root, query, cb -> cb.equal(root.get('enName'), collector.enName)}) {
            return ApiResp.fail("$collector.enName 已存在")
        }
        if (repo.find(DataCollector) {root, query, cb -> cb.equal(root.get('cnName'), collector.cnName)}) {
            return ApiResp.fail("$collector.cnName 已存在")
        }
        repo.saveOrUpdate(collector)
        ep.fire('addDataCollector', collector.enName)
        ep.fire('remote', EC.of(this).attr('toAll', true).args(app.name, 'addDataCollector', [collector.enName]))
        ep.fire('enHistory', collector, ctx.getSessionAttr('name'))
        ApiResp.ok(collector)
    }


    @Path(path = 'updateField', method = 'post')
    ApiResp updateField(HttpContext ctx, Long id, String enName, String cnName, FieldType type, String comment, String dataCollector) {
        ctx.auth('field-update')
        if (!id) return ApiResp.fail("id not legal")
        if (!enName) return ApiResp.fail("enName must not be empty")
        if (!cnName) return ApiResp.fail("cnName must not be empty")
        if (!type) return ApiResp.fail("type must not be empty")
        def field = repo.findById(RuleField, id)
        if (field == null) return ApiResp.fail("id: $id not found")
        if (enName != field.enName) return ApiResp.fail('enName can not change')
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
        ep.fire('updateField', field.enName)
        ep.fire('remote', EC.of(this).attr('toAll', true).args(app.name, 'updateField', [field.enName]))
        ep.fire('enHistory', field, ctx.getSessionAttr('name'))
        ApiResp.ok(field)
    }


    @Path(path = 'updateDataCollector', method = 'post')
    ApiResp updateDataCollector(
        HttpContext ctx, Long id, String enName, String cnName, String url, String bodyStr,
        String method, String parseScript, String contentType, String comment, String computeScript,
        String sqlScript, Integer minIdle, Integer maxActive, Integer timeout
    ) {
        ctx.auth('dataCollector-update')
        if (!id) return ApiResp.fail("id not legal")
        if (!enName) return ApiResp.fail("enName must not be empty")
        if (!cnName) return ApiResp.fail("cnName must not be empty")
        def collector = repo.findById(DataCollector, id)
        if (collector == null) return ApiResp.fail("id: $id not found")
        if ('http' == collector.type) {
            if (!url) return ApiResp.fail('url must not be empty')
            if (!method) return ApiResp.fail('method must not be empty')
            if (!contentType) return ApiResp.fail('contentType must not be empty')
            if (!url.startsWith("http")) return ApiResp.fail('url incorrect')
            collector.parseScript = parseScript?.trim()
            if (collector.parseScript && (!collector.parseScript.startsWith('{') || !collector.parseScript.endsWith('}'))) {
                return ApiResp.fail('parseScript is not a function, must startWith {, endWith }')
            }
            collector.url = url
            collector.method = method
            collector.contentType = contentType
            collector.bodyStr = bodyStr
            collector.timeout = timeout
        } else if ('script' == collector.type) {
            if (!computeScript) return ApiResp.fail('computeScript must not be empty')
            collector.computeScript = computeScript?.trim()
            if (collector.computeScript && (collector.computeScript.startsWith('{') || collector.computeScript.endsWith('}'))) {
                return ApiResp.fail('computeScript is pure script. cannot startWith { or endWith }')
            }
        } else if ('sql' == collector.type) {
            if (!sqlScript) return ApiResp.fail('sqlScript must not be empty')
            if (minIdle < 0 || minIdle > 50) return ApiResp.fail('0 <= minIdle <= 50')
            if (maxActive < 1 || maxActive > 100) return ApiResp.fail('1 <= minIdle <= 100')
            collector.minIdle = minIdle
            collector.maxActive = maxActive
            collector.sqlScript = sqlScript
        }
        def updateRelateField
        if (enName != collector.enName ) {// 不让修改名字
            return ApiResp.fail('enName can not change')
            if (repo.count(DataCollector) {root, query, cb -> cb.equal(root.get('enName'), enName)}) {
                return ApiResp.fail("$enName aleady exist")
            }
            collector.enName = enName
            updateRelateField = { // 修改RuleField相关联
                repo.find(RuleField) {root, query, cb -> cb.equal(root.get('dataCollector'), collector.cnName)}.each {field ->
                    field.dataCollector = enName
                    repo.saveOrUpdate(field)
                }
            }
        }
        if (cnName != collector.cnName) {
            if (repo.count(DataCollector) {root, query, cb -> cb.equal(root.get('cnName'), cnName)}) {
                return ApiResp.fail("$cnName aleady exist")
            }
            collector.cnName = cnName
        }
        collector.comment = comment

        if (updateRelateField) {
            repo.trans {
                repo.saveOrUpdate(collector)
                updateRelateField()
            }
        } else {
            repo.saveOrUpdate(collector)
        }
        ep.fire('updateDataCollector', collector.enName)
        ep.fire('remote', EC.of(this).attr('toAll', true).args(app.name, 'updateDataCollector', [collector.enName]))
        ep.fire('enHistory', collector, ctx.getSessionAttr('name'))
        ApiResp.ok(collector)
    }


    @Path(path = 'delDecision/:decisionId')
    ApiResp delDecision(HttpContext ctx, String decisionId) {
        repo.delete(repo.find(Decision) {root, query, cb -> cb.equal(root.get('decisionId'), decisionId)})
        ctx.auth('decision-del')
        ep.fire('delDecision', decisionId)
        ep.fire('remote', EC.of(this).attr('toAll', true).args(app.name, 'delDecision', [decisionId]))
        ApiResp.ok()
    }


    @Path(path = 'delField/:enName')
    ApiResp delField(HttpContext ctx, String enName) {
        ctx.auth('field-del')
        repo.delete(repo.find(RuleField) {root, query, cb -> cb.equal(root.get('enName'), enName)})
        ep.fire('delField', enName)
        ep.fire('remote', EC.of(this).attr('toAll', true).args(app.name, 'delField', [enName]))
        ApiResp.ok()
    }


    @Path(path = 'delDataCollector/:enName')
    ApiResp delDataCollector(HttpContext ctx, String enName) {
        ctx.auth('dataCollector-del')
        repo.delete(repo.find(DataCollector) {root, query, cb -> cb.equal(root.get('enName'), enName)})
        ep.fire('delDataCollector', enName)
        ep.fire('remote', EC.of(this).attr('toAll', true).args(app.name, 'delDataCollector', [enName]))
        ApiResp.ok()
    }


    @Path(path = 'countDecide')
    ApiResp countDecide(String startTime, String endTime, String type) {
        Date start = startTime ? new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(startTime) : null
        Date end = endTime ? new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(endTime) : null
        def cal = Calendar.getInstance()
        if (type == 'today') {
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        } else if (type == 'lastOneHour') {
            cal.add(Calendar.HOUR_OF_DAY, -1)
        } else if (type == 'lastTwoHour') {
            cal.add(Calendar.HOUR_OF_DAY, -2)
        } else if (type == 'lastFiveHour') {
            cal.add(Calendar.HOUR_OF_DAY, -5)
        } else return ApiResp.fail("type: '$type' not supprot")
        ApiResp.ok(repo.trans{se ->
            se.createNativeQuery(
                "select decision_id, decision, count(*) total from decision_result where occur_time>=:occurTime group by decision_id, decision"
            )
                .setParameter("occurTime", cal.getTime())
                .unwrap(NativeQueryImpl).setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP)
                .list()
        }.collect {Map<String, Object> record ->
            def data = new HashMap<>(5)
            record.each {e ->
                data.put(e.key.toLowerCase(), e.value)
            }
            data['decisionName'] = bean(DecisionManager).findDecision(data['decision_id']).决策名
            data
        })
    }
}

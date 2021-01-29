package ctrl

import cn.xnatural.app.ServerTpl
import cn.xnatural.app.Utils
import cn.xnatural.http.ApiResp
import cn.xnatural.http.Ctrl
import cn.xnatural.http.HttpContext
import cn.xnatural.http.Path
import cn.xnatural.jpa.Page
import cn.xnatural.jpa.Repo
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import entity.*
import service.rule.DecisionEnum
import service.rule.DecisionManager
import service.rule.FieldManager
import service.rule.spec.DecisionSpec

import javax.persistence.criteria.Predicate
import java.text.SimpleDateFormat
import java.util.Map.Entry

@Ctrl(prefix = 'mnt')
class MntDecisionCtrl extends ServerTpl {

    @Lazy def repo = bean(Repo, 'jpa_rule_repo')


    @Path(path = 'decisionPage')
    ApiResp decisionPage(HttpContext hCtx, Integer page, Integer pageSize, String kw, String nameLike, String decisionId) {
        if (pageSize && pageSize > 20) return ApiResp.fail("Param pageSize <=20")
        hCtx.auth("decision-read")
        // 允许访问的决策id
        def ids = hCtx.getSessionAttr("permissions").split(",").findResults {String p -> p.replace("decision-read-", "").replace("decision-read", "")}.findAll {it}
        if (!ids) return ApiResp.ok(Page.empty())
        def delIds = hCtx.getSessionAttr("permissions").split(",").findResults {String p -> p.replace("decision-del-", "").replace("decision-del", "")}.findAll {it}
        def updateIds = hCtx.getSessionAttr("permissions").split(",").findResults {String p -> p.replace("decision-update-", "").replace("decision-update", "")}.findAll {it}
        ApiResp.ok(
                repo.findPage(Decision, page, pageSize?:10) {root, query, cb ->
                    query.orderBy(cb.desc(root.get('updateTime')))
                    def ps = []
                    ps << root.get("id").in(ids)
                    if (decisionId) ps << cb.equal(root.get('decisionId'), decisionId)
                    if (nameLike) ps << cb.like(root.get('name'), '%' + nameLike + '%')
                    if (kw) ps << cb.like(root.get('dsl'), '%' + kw + '%')
                    cb.and(ps.toArray(new Predicate[ps.size()]))
                }.to{decision ->
                    Utils.toMapper(decision).add("_deletable", delIds?.contains(decision.id)).add("_readonly", !updateIds?.contains(decision.id)).build()
                }
        )
    }


    @Path(path = 'fieldPage')
    ApiResp fieldPage(HttpContext hCtx, Integer page, Integer pageSize, String kw) {
        if (pageSize && pageSize > 50) return ApiResp.fail("Param pageSize <=50")
        hCtx.auth("field-read")
        def fieldPage = repo.findPage(RuleField, page, (pageSize?:10)) { root, query, cb ->
            query.orderBy(cb.desc(root.get('updateTime')))
            if (kw) {
                cb.or(
                        cb.like(root.get('enName'), '%' + kw + '%'),
                        cb.like(root.get('cnName'), '%' + kw + '%'),
                        cb.like(root.get('comment'), '%' + kw + '%')
                )
            }
        }.to{ Utils.toMapper(it).ignore("metaClass").build()}
        def collectorNames = fieldPage.list.collect {it.dataCollector}.findAll{it}.toSet()
        if (collectorNames) {
            repo.findList(DataCollector) {root, query, cb -> root.get('enName').in(collectorNames)}.each {dc ->
                fieldPage.list.findAll {it.dataCollector == dc.enName}?.each {it.dataCollectorName = dc.cnName}
            }
        }
        ApiResp.ok(fieldPage)
    }


    @Path(path = 'dataCollectorPage')
    ApiResp dataCollectorPage(HttpContext hCtx, Integer page, Integer pageSize, String kw, String enName, String type) {
        if (pageSize && pageSize > 50) return ApiResp.fail("Param pageSize <=50")
        hCtx.auth("dataCollector-read")
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
                if (enName) ps << cb.equal(root.get('enName'), enName)
                if (type) ps << cb.equal(root.get('type'), type)
                cb.and(ps.toArray(new Predicate[ps.size()]))
            }
        )
    }


    @Path(path = 'opHistoryPage')
    ApiResp opHistoryPage(HttpContext hCtx, Integer page, Integer pageSize, String kw, String type) {
        if (pageSize && pageSize > 20) return ApiResp.fail("Param pageSize <=20")
        hCtx.auth("opHistory-read")
        ApiResp.ok(
            repo.findPage(OpHistory, page, pageSize?:10) { root, query, cb ->
                query.orderBy(cb.desc(root.get('createTime')))
                def ps = []
                if (kw) {
                    ps << cb.like(root.get('content'), '%' + kw + '%')
                }
                if (type) {
                    ps << cb.equal(root.get('tbName'), repo.tbName(Class.forName(Decision.package.name + "." + type)).replace("`", ""))
                }
                cb.and(ps.toArray(new Predicate[ps.size()]))
            }
        )
    }


    @Path(path = 'decisionResultPage')
    ApiResp decisionResultPage(
        HttpContext hCtx, Integer page, Integer pageSize, String id, String decisionId, DecisionEnum decision,
        String idNum, Long spend, String exception, String input, String attrs, String rules, String startTime, String endTime
    ) {
        hCtx.auth("decisionResult-read")
        if (pageSize && pageSize > 10) return ApiResp.fail("Param pageSize <=10")
        def ids = hCtx.getSessionAttr("permissions").split(",").findResults {String p -> p.replace("decision-read-", "").replace("decision-read", "")}.findAll {it}
        if (!ids) return ApiResp.ok()
        Date start = startTime ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(startTime) : null
        Date end = endTime ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(endTime) : null
        ApiResp.ok(
                repo.findPage(DecisionResult, page, pageSize?:10) { root, query, cb ->
                    query.orderBy(cb.desc(root.get('occurTime')))
                    def ps = []
                    ps << root.get('decisionId').in(ids)
                    if (id) ps << cb.equal(root.get('id'), id)
                    if (start) ps << cb.greaterThanOrEqualTo(root.get('occurTime'), start)
                    if (end) ps << cb.lessThanOrEqualTo(root.get('occurTime'), end)
                    if (decisionId) ps << cb.equal(root.get('decisionId'), decisionId)
                    if (idNum) ps << cb.equal(root.get('idNum'), idNum)
                    if (spend) ps << cb.ge(root.get('spend'), spend)
                    if (decision) ps << cb.equal(root.get('decision'), decision)
                    if (exception) ps << cb.like(root.get('exception'), '%' + exception + '%')
                    if (input) ps << cb.like(root.get('input'), '%' + input + '%')
                    if (attrs) ps << cb.like(root.get('attrs'), '%' + attrs + '%')
                    if (rules) ps << cb.like(root.get('rules'), '%' + rules + '%')
                    if (ps) cb.and(ps.toArray(new Predicate[ps.size()]))
                }.to{
                    def am = bean(FieldManager)
                    Utils.toMapper(it).ignore("metaClass")
                            .addConverter('decisionId', 'decisionName', { String dId ->
                                bean(DecisionManager).decisionMap.find {it.value.decision.id == dId}?.value?.decision?.name
                            }).addConverter('attrs', {
                        it == null ? null : JSON.parseObject(it).collect { e ->
                            [enName: e.key, cnName: am.fieldMap.get(e.key)?.cnName, value: e.value]
                        }}).addConverter('input', {
                        it == null ? null : JSON.parseObject(it)
                    }).addConverter('dataCollectResult', {
                        it == null ? null : JSON.parseObject(it)
                    }).addConverter('rules', {
                        def arr = it == null ? null : JSON.parseArray(it)
                        arr?.each { JSONObject jo ->
                            jo.put('data', jo.getJSONObject('data').collect { Entry<String, Object> e ->
                                [enName: e.key, cnName: am.fieldMap.get(e.key)?.cnName, value: e.value]
                            })
                        }
                        arr
                    }).build()
                }
        )
    }


    @Path(path = 'collectResultPage')
    ApiResp collectResultPage(
        HttpContext hCtx, Integer page, Integer pageSize, String decideId, String collectorType, String collector, String decisionId,
        Long spend, Boolean success, Boolean dataSuccess, Boolean cache, String startTime, String endTime
    ) {
        hCtx.auth("collectResult-read")
        def ids = hCtx.getSessionAttr("permissions").split(",").findResults {String p -> p.replace("decision-read-", "").replace("decision-read", "")}.findAll {it}
        if (!ids) return ApiResp.ok()
        Date start = startTime ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(startTime) : null
        Date end = endTime ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(endTime) : null
        if (pageSize && pageSize > 50) return ApiResp.fail("Param pageSize <=50")
        def result = repo.findPage(CollectResult, page, pageSize?:10) { root, query, cb ->
            query.orderBy(cb.desc(root.get('collectDate')))
            def ps = []
            ps << root.get('decisionId').in(ids)
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
            if (dataSuccess != null) {
                if (dataSuccess) {
                    ps << cb.equal(root.get('dataStatus'), '0000')
                } else {
                    ps << cb.notEqual(root.get('dataStatus'), '0000')
                }
            }
            if (cache != null) {
                if (cache) {
                    ps << cb.equal(root.get('cache'), true)
                } else {
                    ps << cb.notEqual(root.get('cache'), true)
                }
            }
            if (ps) cb.and(ps.toArray(new Predicate[ps.size()]))
        }.to{record -> Utils.toMapper(record).ignore("metaClass")
                .addConverter('decisionId', 'decisionName', {String dId ->
                    bean(DecisionManager).decisionMap.find {it.value.decision.id == dId}?.value?.decision?.name
                }).build()
        }
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
     * @param apiConfig api配置
     * @param hCtx {@link HttpContext}
     * @return
     */
    @Path(path = 'setDecision', method = 'post')
    ApiResp setDecision(String id, String dsl, String apiConfig, HttpContext hCtx) {
        if (!dsl) return ApiResp.fail("Param dsl not empty")
        DecisionSpec spec
        try {
            spec = DecisionSpec.of(dsl)
        } catch (ex) {
            log.error("语法错误", ex)
            return ApiResp.fail('语法错误: ' + ex.message)
        }

        //dsl 验证
        if (!spec.决策id) return ApiResp.fail("决策id 不能为空")
        if (!spec.决策名) return ApiResp.fail("决策名 不能为空")
        if (!spec.policies) return ApiResp.fail("${spec.决策名} 是空决策")
        for (def policy : spec.policies) {
            if (!policy.策略名) return ApiResp.fail('策略名字不能为空')
            if (!policy.rules) return ApiResp.fail("'${policy.策略名}' 是空策略")
            for (def rule : policy.rules) {
                if (!rule.规则名) return ApiResp.fail('规则名字不能为空')
                if (rule.decisionFn.size() < 1) return ApiResp.fail("'${rule.规则名}' 是空规则")
            }
        }

        Decision decision
        if (id) { // 更新
            decision = repo.findById(Decision, id)
            hCtx.auth('decision-update-' + decision.id)
            if (decision.decisionId != spec.决策id) {
                if (repo.find(Decision) {root, query, cb -> cb.equal(root.get('decisionId'), spec.决策id)}) { // 决策id 不能重
                    return ApiResp.fail("决策id($spec.决策id)已存在")
                }
            }
            decision.updater = hCtx.getSessionAttr("uName")
        } else { // 创建
            hCtx.auth('decision-add')
            decision = new Decision()
            decision.creator = hCtx.getSessionAttr("uName")
        }
        decision.decisionId = spec.决策id
        decision.name = spec.决策名
        decision.comment = spec.决策描述
        decision.dsl = dsl
        if (apiConfig) { //矫正decisionId参数
            def params = JSON.parseArray(apiConfig)
            JSONObject param = params.find {JSONObject jo -> "decisionId" == jo.getString("code")}
            if (param) {
                param.put("fixValue", decision.decisionId)
            } else {
                params.add(0, new JSONObject().fluentPut("code", "decisionId").fluentPut("type", "Str")
                        .fluentPut("fixValue", decision.decisionId).fluentPut("name", "决策id").fluentPut("require", true)
                )
            }
            for (def it = params.iterator(); it.hasNext(); ) {
                JSONObject jo = it.next()
                if (!jo.getString("code") || !jo.getString("name")) it.remove()
            }
            apiConfig = params.toString()
        } else {
            apiConfig = new JSONArray().add(
                new JSONObject().fluentPut("code", "decisionId").fluentPut("type", "Str")
                    .fluentPut("fixValue", decision.decisionId).fluentPut("name", "决策id").fluentPut("require", true)
            ).toString()
        }
        decision.apiConfig = apiConfig

        repo.saveOrUpdate(decision)
        ep.fire("decisionChange", decision.id)
        ep.fire('enHistory', decision, hCtx.getSessionAttr('uName'))
        ApiResp.ok(decision)
    }


    @Path(path = 'addField', method = 'post')
    ApiResp addField(HttpContext hCtx, String enName, String cnName, FieldType type, String comment, String dataCollector) {
        hCtx.auth('field-add')
        if (!enName) return ApiResp.fail("Param enName not empty")
        if (!cnName) return ApiResp.fail("Param cnName not empty")
        if (!type) return ApiResp.fail("Param type not empty")
        if (repo.count(RuleField) {root, query, cb -> cb.equal(root.get('enName'), enName)}) return ApiResp.fail("$enName aleady exist")
        if (repo.count(RuleField) {root, query, cb -> cb.equal(root.get('cnName'), cnName)}) return ApiResp.fail("$cnName aleady exist")
        def field = new RuleField(enName: enName, cnName: cnName, type: type, comment: comment, dataCollector: dataCollector, creator: hCtx.getSessionAttr('uName'))
        repo.saveOrUpdate(field)
        ep.fire('fieldChange', field.enName)
        ep.fire('enHistory', field, hCtx.getSessionAttr('uName'))
        ApiResp.ok(field)
    }


    @Path(path = 'addDataCollector', method = 'post')
    ApiResp addDataCollector(
        HttpContext hCtx, String enName, String cnName, String type, String url, String bodyStr,
        String method, String parseScript, String contentType, String comment, String computeScript, String dataSuccessScript,
        String sqlScript, Integer minIdle, Integer maxActive, Integer timeout, Boolean enabled, String cacheKey, Integer cacheTimeout
    ) {
        hCtx.auth('dataCollector-add')
        DataCollector collector = new DataCollector(enName: enName, cnName: cnName, type: type, comment: comment, enabled: (enabled == null ? true : enabled))
        if (!collector.enName) return ApiResp.fail('Param enName not empty')
        if (!collector.cnName) return ApiResp.fail('Param cnName not empty')
        if (!collector.type) return ApiResp.fail('Param type not empty')
        if ('http' == collector.type) {
            if (!url) return ApiResp.fail('Param url not empty')
            if (!method) return ApiResp.fail('Param method not empty')
            if (!contentType) return ApiResp.fail('Param contentType not empty')
            if (!url.startsWith("http") && !url.startsWith('${')) return ApiResp.fail('Param url incorrect')
            collector.parseScript = parseScript?.trim()
            if (collector.parseScript && !collector.parseScript.startsWith("{") && !collector.parseScript.endsWith("}")) {
                return ApiResp.fail('Param parseScript a function, must startWith {, endWith }')
            }
            collector.dataSuccessScript = dataSuccessScript?.trim()
            if (collector.dataSuccessScript && !collector.dataSuccessScript.startsWith("{") && !collector.dataSuccessScript.endsWith("}")) {
                return ApiResp.fail('Param dataSuccessScript a function, must startWith {, endWith }')
            }
            collector.url = url
            collector.method = method
            collector.bodyStr = bodyStr
            collector.contentType = contentType
            collector.timeout = timeout
        } else if ('script' == collector.type) {
            if (!computeScript) return ApiResp.fail('Param computeScript not empty')
            collector.computeScript = computeScript
        } else if ('sql' == collector.type) {
            if (!url) return ApiResp.fail('Param url not empty')
            if (!sqlScript) return ApiResp.fail('Param sqlScript not empty')
            if (!url.startsWith("jdbc")) return ApiResp.fail('url incorrect')
            if (minIdle < 0 || collector.minIdle > 20) return ApiResp.fail('Param minIdle >=0 and <= 20')
            if (maxActive < 1 || collector.maxActive > 50) ApiResp.fail('Param maxActive >=1 and <= 50')
            collector.url = url
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
        collector.creator = hCtx.getSessionAttr("uName")
        collector.cacheKey = cacheKey
        collector.cacheTimeout = cacheTimeout

        repo.saveOrUpdate(collector)
        ep.fire('dataCollectorChange', collector.enName)
        ep.fire('enHistory', collector, hCtx.getSessionAttr('uName'))
        ApiResp.ok(collector)
    }


    @Path(path = 'updateField', method = 'post')
    ApiResp updateField(HttpContext hCtx, Long id, String enName, String cnName, FieldType type, String comment, String dataCollector) {
        hCtx.auth('field-update', 'field-update-' + enName)
        if (!id) return ApiResp.fail("Param id not legal")
        if (!enName) return ApiResp.fail("Param enName not empty")
        if (!cnName) return ApiResp.fail("Param cnName not empty")
        if (!type) return ApiResp.fail("Param type not empty")
        def field = repo.findById(RuleField, id)
        if (field == null) return ApiResp.fail("Param id: $id not found")
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
        field.updater = hCtx.getSessionAttr("uName")
        repo.saveOrUpdate(field)
        ep.fire('fieldChange', field.enName)
        ep.fire('enHistory', field, hCtx.getSessionAttr('uName'))
        ApiResp.ok(field)
    }


    @Path(path = 'updateDataCollector', method = 'post')
    ApiResp updateDataCollector(
        HttpContext hCtx, Long id, String enName, String cnName, String url, String bodyStr,
        String method, String parseScript, String contentType, String comment, String computeScript, String dataSuccessScript,
        String sqlScript, Integer minIdle, Integer maxActive, Integer timeout, Boolean enabled, String cacheKey, Integer cacheTimeout
    ) {
        hCtx.auth('dataCollector-update', 'dataCollector-update-' + enName)
        if (!id) return ApiResp.fail("Param id not legal")
        if (!enName) return ApiResp.fail("Param enName not empty")
        if (!cnName) return ApiResp.fail("Param cnName not empty")
        def collector = repo.findById(DataCollector, id)
        if (collector == null) return ApiResp.fail("Param id: $id not found")
        if ('http' == collector.type) {
            if (!url) return ApiResp.fail('Param url not empty')
            if (!method) return ApiResp.fail('Param method not empty')
            if (!contentType) return ApiResp.fail('Param contentType not empty')
            if (!url.startsWith("http") && !url.startsWith('${')) return ApiResp.fail('Param url incorrect')
            collector.parseScript = parseScript?.trim()
            if (collector.parseScript && (!collector.parseScript.startsWith('{') || !collector.parseScript.endsWith('}'))) {
                return ApiResp.fail('Param parseScript is function, must startWith {, endWith }')
            }
            collector.dataSuccessScript = dataSuccessScript?.trim()
            if (collector.dataSuccessScript && !collector.dataSuccessScript.startsWith("{") && !collector.dataSuccessScript.endsWith("}")) {
                return ApiResp.fail('Param dataSuccessScript is function, must startWith {, endWith }')
            }
            collector.url = url
            collector.method = method
            collector.contentType = contentType
            collector.bodyStr = bodyStr
            collector.timeout = timeout
        } else if ('script' == collector.type) {
            if (!computeScript) return ApiResp.fail('Param computeScript not empty')
            collector.computeScript = computeScript?.trim()
            if (collector.computeScript && (collector.computeScript.startsWith('{') || collector.computeScript.endsWith('}'))) {
                return ApiResp.fail('Param computeScript is pure script. cannot startWith { or endWith }')
            }
        } else if ('sql' == collector.type) {
            if (!url) return ApiResp.fail('Param url not empty')
            if (!sqlScript) return ApiResp.fail('Param sqlScript not empty')
            if (!url.startsWith("jdbc")) return ApiResp.fail('Param url incorrect')
            if (minIdle < 0 || minIdle > 20) return ApiResp.fail('Param minIdle >=0 and <= 20')
            if (maxActive < 1 || maxActive > 50) return ApiResp.fail('Param maxActive >=1 and <= 50')
            collector.url = url
            collector.minIdle = minIdle
            collector.maxActive = maxActive
            collector.sqlScript = sqlScript
        }
        def updateRelateField
        if (enName != collector.enName) {
            return ApiResp.fail('enName can not change') // 不让修改名字
            if (repo.count(DataCollector) {root, query, cb -> cb.equal(root.get('enName'), enName)}) {
                return ApiResp.fail("$enName aleady exist")
            }
            collector.enName = enName
            updateRelateField = { // 修改RuleField相关联
                repo.find(RuleField) {root, query, cb -> cb.equal(root.get('dataCollector'), collector.cnName)}.each {field ->
                    field.dataCollector = enName

                    repo.saveOrUpdate(field)
                    ep.fire('fieldChange', field.enName)
                    ep.fire('enHistory', field, hCtx.getSessionAttr('uName'))
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
        collector.enabled = enabled
        collector.updater = hCtx.getSessionAttr("uName")
        collector.cacheKey = cacheKey
        collector.cacheTimeout = cacheTimeout

        if (updateRelateField) {
            repo.trans {
                repo.saveOrUpdate(collector)
                updateRelateField()
            }
        } else {
            repo.saveOrUpdate(collector)
        }
        ep.fire('dataCollectorChange', collector.enName)
        ep.fire('enHistory', collector, hCtx.getSessionAttr('uName'))
        ApiResp.ok(collector)
    }


    @Path(path = 'delDecision/:id')
    ApiResp delDecision(HttpContext hCtx, String id) {
        if (!id) return ApiResp.fail("Param id required")
        hCtx.auth( 'decision-del-' + id)
        // log.info("delDecision. decisionId: {}, byUser: {}", decisionId, hCtx.getSessionAttr("uName"))
        def decision = repo.find(Decision) {root, query, cb -> cb.equal(root.get('id'), id)}
        repo.delete(decision)
        ep.fire('decisionChange', decision.id)
        ep.fire('enHistory', decision, hCtx.getSessionAttr('uName'))
        ApiResp.ok()
    }


    @Path(path = 'delField/:enName')
    ApiResp delField(HttpContext hCtx, String enName) {
        if (!enName) return ApiResp.fail("Param enName not empty")
        hCtx.auth('field-del', 'field-del-' + enName)
        def field = repo.find(RuleField) {root, query, cb -> cb.equal(root.get('enName'), enName)}
        repo.delete(field)
        ep.fire('fieldChange', enName)
        ep.fire('enHistory', field, hCtx.getSessionAttr('uName'))
        ApiResp.ok()
    }


    @Path(path = 'delDataCollector/:enName')
    ApiResp delDataCollector(HttpContext hCtx, String enName) {
        if (!enName) return ApiResp.fail("Param enName not empty")
        hCtx.auth('dataCollector-del', 'dataCollector-del-' + enName)
        def collector = repo.find(DataCollector) {root, query, cb -> cb.equal(root.get('enName'), enName)}
        repo.delete(collector)
        ep.fire('dataCollectorChange', enName)
        ep.fire('enHistory', collector, hCtx.getSessionAttr('uName'))
        ApiResp.ok()
    }


    @Path(path = 'testCollector/:collector')
    ApiResp testCollector(String collector, HttpContext hCtx) {
        ApiResp.ok(bean(FieldManager)?.testCollector(collector, hCtx.params()))
    }
}

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
import com.alibaba.fastjson.parser.Feature
import entity.*
import service.rule.DecideResult
import service.rule.DecisionManager
import service.rule.DecisionSrv
import service.rule.FieldManager
import service.rule.spec.DecisionSpec

import javax.persistence.criteria.Predicate
import java.text.SimpleDateFormat
import java.util.Map.Entry

@Ctrl(prefix = 'mnt')
class MntDecisionCtrl extends ServerTpl {

    @Lazy def repo = bean(Repo, 'jpa_rule_repo')
    @Lazy def decisionManager = bean(DecisionManager)
    @Lazy def fieldManager = bean(FieldManager)


    @Path(path = 'decisionPage')
    ApiResp decisionPage(HttpContext hCtx, Integer page, Integer pageSize, String kw, String nameLike, String decisionId) {
        if (pageSize && pageSize > 20) return ApiResp.fail("Param pageSize <=20")
        // hCtx.auth("decision-read")
        // 允许访问的决策id
        def ids = hCtx.getSessionAttr("permissions").split(",")
            .findAll {String p -> p.startsWith("decision-read-")}
            .findResults {String p -> p.replace("decision-read-", "")}
            .findAll {it}
        if (!ids) return ApiResp.ok(Page.empty())
        def delIds = hCtx.getSessionAttr("permissions").split(",")
            .findAll {String p -> p.startsWith("decision-del-")}
            .findResults {String p -> p.replace("decision-del-", "")}
            .findAll {it}
        def updateIds = hCtx.getSessionAttr("permissions").split(",")
            .findAll {String p -> p.startsWith("decision-update-")}
            .findResults {String p -> p.replace("decision-update-", "")}
            .findAll {it}
        ApiResp.ok(
                repo.findPage(Decision, page, pageSize?:10) {root, query, cb ->
                    query.orderBy(cb.desc(root.get('updateTime')))
                    def ps = []
                    ps << root.get("id").in(ids)
                    if (decisionId) ps << cb.equal(root.get('id'), decisionId)
                    if (nameLike) ps << cb.or(cb.like(root.get('name'), '%' + nameLike + '%'), cb.like(root.get('decisionId'), '%' + nameLike + '%'))
                    if (kw) ps << cb.like(root.get('dsl'), '%' + kw + '%')
                    cb.and(ps.toArray(new Predicate[ps.size()]))
                }.to{decision ->
                    Utils.toMapper(decision).add("_deletable", delIds?.contains(decision.id)).add("_readonly", !updateIds?.contains(decision.id)).build()
                }
        )
    }


    @Path(path = 'fieldPage')
    ApiResp fieldPage(HttpContext hCtx, Integer page, Integer pageSize, String collector, String decision, String kw) {
        if (pageSize && pageSize > 50) return ApiResp.fail("Param pageSize <=50")
        hCtx.auth("field-read")
        ApiResp.ok(repo.findPage(RuleField, page, (pageSize?:10)) { root, query, cb ->
            query.orderBy(cb.desc(root.get('updateTime')))
            def ps = []
            if (kw) {
                ps << cb.or(
                        cb.like(root.get('enName'), '%' + kw + '%'),
                        cb.like(root.get('cnName'), '%' + kw + '%'),
                        cb.like(root.get('comment'), '%' + kw + '%')
                )
            }
            if (collector) {
                ps << cb.equal(root.get("dataCollector"), collector)
            }
            if (decision) {
                ps << cb.equal(root.get("decision"), decision)
            }
            cb.and(ps.toArray(new Predicate[ps.size()]))
        }.to{ Utils.toMapper(it).ignore("metaClass")
            .addConverter("decision", "decisionName") {dId -> dId ? decisionManager.decisionMap.find {it.value.decision.id == dId}?.value?.decision?.name : null}
            .addConverter("decision", "decisionId") {dId -> dId ? decisionManager.decisionMap.find {it.value.decision.id == dId}?.value?.decision?.decisionId : null}
            .addConverter("dataCollector", "dataCollectorName") {collectorId -> collectorId ? fieldManager.collectors.get(collectorId)?.collector?.name : null}
            .build()
        })
    }


    @Path(path = 'dataCollectorPage')
    ApiResp dataCollectorPage(HttpContext hCtx, Integer page, Integer pageSize, String kw, String id, String type) {
        if (pageSize && pageSize > 50) return ApiResp.fail("Param pageSize <=50")
        hCtx.auth("dataCollector-read")
        ApiResp.ok(
            repo.findPage(DataCollector, page, pageSize?:10) { root, query, cb ->
                query.orderBy(cb.desc(root.get('updateTime')))
                def ps = []
                if (kw) {
                    ps << cb.or(
                        cb.like(root.get('name'), '%' + kw + '%'),
                        cb.like(root.get('comment'), '%' + kw + '%')
                    )
                }
                if (id) ps << cb.equal(root.get("id"), id)
                if (type) ps << cb.equal(root.get('type'), type)
                cb.and(ps.toArray(new Predicate[ps.size()]))
            }.to {Utils.toMapper(it).ignore("metaClass", "cacheTimeout")
                    .addConverter("cacheTimeoutFn", "cacheTimeout") {String fn -> fn.replace("{ -> ", "").replace("}", "")}
                    .build()
            }
        )
    }


    @Path(path = 'opHistoryPage')
    ApiResp opHistoryPage(HttpContext hCtx, Integer page, Integer pageSize, String kw, String type, String startTime, String endTime) {
        if (pageSize && pageSize > 20) return ApiResp.fail("Param pageSize <=20")
        hCtx.auth("opHistory-read")
        Date start = startTime ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(startTime) : null
        Date end = endTime ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(endTime) : null
        ApiResp.ok(
            repo.findPage(OpHistory, page, pageSize?:5) { root, query, cb ->
                query.orderBy(cb.desc(root.get('createTime')))
                def ps = []
                if (kw) ps << cb.like(root.get('content'), '%' + kw + '%')
                if (start) ps << cb.greaterThanOrEqualTo(root.get('createTime'), start)
                if (end) ps << cb.lessThanOrEqualTo(root.get('createTime'), end)
                if (type) {
                    ps << cb.equal(root.get('tbName'), repo.tbName(Class.forName(Decision.package.name + "." + type)).replace("`", ""))
                }
                cb.and(ps.toArray(new Predicate[ps.size()]))
            }
        )
    }


    @Path(path = 'decisionResultPage')
    ApiResp decisionResultPage(
            HttpContext hCtx, Integer page, Integer pageSize, String id, String decisionId, DecideResult result,
            Long spend, String exception, String attrConditions, String startTime, String endTime
    ) {
        hCtx.auth("decideResult-read")
        if (pageSize && pageSize > 10) return ApiResp.fail("Param pageSize <=10")
        def ids = hCtx.getSessionAttr("permissions").split(",")
            .findAll {String p -> p.startsWith("decision-read-")}
            .findResults {String p -> p.replace("decision-read-", "")}
            .findAll {it}
        if (!ids) return ApiResp.ok()
        Date start = startTime ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(startTime) : null
        Date end = endTime ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(endTime) : null
        ApiResp.ok(
                repo.findPage(DecideRecord, page, pageSize?:10) { root, query, cb ->
                    def ps = []
                    if (start) ps << cb.greaterThanOrEqualTo(root.get('occurTime'), start)
                    if (end) ps << cb.lessThanOrEqualTo(root.get('occurTime'), end)
                    if (id) ps << cb.equal(root.get('id'), id)
                    ps << root.get('decisionId').in(ids)
                    if (decisionId) ps << cb.equal(root.get('decisionId'), decisionId)
                    if (spend) ps << cb.ge(root.get('spend'), spend)
                    if (result) ps << cb.equal(root.get('result'), result)
                    if (exception) ps << cb.like(root.get('exception'), '%' + exception + '%')
                    def orders = []
                    if (attrConditions) { // json查询 暂时只支持mysql5.7+,MariaDB 10.2.3+
                        JSON.parseArray(attrConditions).each {JSONObject jo ->
                            def fieldId = jo.getLong('fieldId')
                            if (!fieldId) return
                            def field = fieldManager.fieldMap.find {it.value.id == fieldId}?.value
                            if (field == null) return

                            def exp = cb.function("JSON_EXTRACT", String, root.get('data'), cb.literal('$.' + field.enName))
                            def op = jo['op']
                            if (op == "desc") { //降序
                                orders << cb.desc(exp.as(field.type.clzType))
                                return
                            } else if (op == 'asc') { //升序
                                orders << cb.asc(exp.as(field.type.clzType))
                                return
                            }
                            def value = jo['value']
                            if (value == null || value.empty) return

                            if (op == '=') {
                                ps << cb.equal(exp, value)
                            } else if (op == '>') {
                                ps << cb.gt(exp.as(field.type.clzType), Utils.to(value, field.type.clzType))
                            } else if (op == '<') {
                                ps << cb.lt(exp.as(field.type.clzType), Utils.to(value, field.type.clzType))
                            } else if (op == '>=') {
                                ps << cb.ge(exp.as(field.type.clzType), Utils.to(value, field.type.clzType))
                            } else if (op == '<=') {
                                ps << cb.le(exp.as(field.type.clzType), Utils.to(value, field.type.clzType))
                            } else if (op == 'contains') {
                                ps << cb.like(exp, '%' + value + '%')
                            } else throw new IllegalArgumentException("Param attrCondition op('$op') unknown")
                        }
                    }
                    if (orders) { // 按照data中的属性进行排序
                        query.orderBy(orders)
                    } else { // 默认时间降序
                        query.orderBy(cb.desc(root.get('occurTime')))
                    }
                    if (ps) cb.and(ps.toArray(new Predicate[ps.size()]))
                }.to{
                    Utils.toMapper(it).ignore("metaClass")
                            .addConverter('decisionId', 'decisionName', { String dId ->
                                decisionManager.decisionMap.find {it.value.decision.id == dId}?.value?.decision?.name
                            }).addConverter('data', {String jsonStr ->
                                it == null ? null : JSON.parseObject(jsonStr, Feature.OrderedField).collect { e ->
                                    [enName: e.key, cnName: fieldManager.fieldMap.get(e.key)?.cnName, value: e.value]
                                }}).addConverter('input', {jsonStr ->
                                    it == null ? null : JSON.parseObject(jsonStr)
                                }).addConverter('dataCollectResult', {String jsonStr ->
                                    it == null ? null : JSON.parseObject(jsonStr, Feature.OrderedField).collectEntries { e ->
                                        // 格式为: collectorId[_数据key], 把collectorId替换为收集器名字
                                        String collectorId
                                        def arr = e.key.split("_")
                                        if (arr.length == 1) collectorId = e.key
                                        else collectorId = arr[0]
                                        [
                                                (fieldManager.collectors.findResult {
                                                    it.value.collector.id == collectorId ? it.value.collector.name + (arr.length > 1 ? '_' + arr.drop(1).join('_') : '') : null
                                                }?:e.key): e.value
                                        ]
                                    }
                                }).addConverter('detail', {String detailJsonStr ->
                                    if (!detailJsonStr) return null
                                    def detailJo = JSON.parseObject(detailJsonStr, Feature.OrderedField)
                                    // 数据转换
                                    detailJo.put('data', detailJo.getJSONObject('data')?.collect { Entry<String, Object> e ->
                                        [enName: e.key, cnName: fieldManager.fieldMap.get(e.key)?.cnName, value: e.value]
                                    }?:null)
                                    detailJo.getJSONArray('policies')?.each {JSONObject pJo ->
                                        pJo.put('data', pJo.getJSONObject('data')?.collect { Entry<String, Object> e ->
                                            [enName: e.key, cnName: fieldManager.fieldMap.get(e.key)?.cnName, value: e.value]
                                        }?:null)
                                        pJo.getJSONArray('items')?.each {JSONObject rJo ->
                                            rJo.put('data', rJo.getJSONObject('data')?.collect { Entry<String, Object> e ->
                                                [enName: e.key, cnName: fieldManager.fieldMap.get(e.key)?.cnName, value: e.value]
                                            }?:null)
                                        }
                                    }
                                    detailJo
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
        def ids = hCtx.getSessionAttr("permissions").split(",")
            .findAll {String p -> p.startsWith("decision-read-")}
            .findResults {String p -> p.replace("decision-read-", "")}
            .findAll {it}
        if (!ids) return ApiResp.ok()
        Date start = startTime ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(startTime) : null
        Date end = endTime ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(endTime) : null
        if (pageSize && pageSize > 50) return ApiResp.fail("Param pageSize <=50")
        ApiResp.ok(
            repo.findPage(CollectRecord, page, pageSize?:10) { root, query, cb ->
                query.orderBy(cb.desc(root.get('collectDate')))
                def ps = []
                if (start) ps << cb.greaterThanOrEqualTo(root.get('collectDate'), start)
                if (end) ps << cb.lessThanOrEqualTo(root.get('collectDate'), end)
                if (decideId) ps << cb.equal(root.get('decideId'), decideId)
                ps << root.get('decisionId').in(ids)
                if (decisionId) ps << cb.equal(root.get('decisionId'), decisionId)
                if (collectorType) ps << cb.equal(root.get('collectorType'), collectorType)
                if (collector) ps << cb.equal(root.get('collector'), collector)
                if (spend) ps << cb.ge(root.get('spend'), spend)
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
                    decisionManager.decisionMap.find {it.value.decision.id == dId}?.value?.decision?.name
                }).addConverter('collector', 'collectorName', {String cId ->
                    fieldManager.collectors.get(cId)?.collector?.name
                }).build()
            }
        )
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
                param.fluentPut("fixValue", decision.decisionId).fluentPut("type", "Str").fluentPut("require", true)
            } else {
                params.add(0, new JSONObject().fluentPut("code", "decisionId").fluentPut("type", "Str")
                        .fluentPut("fixValue", decision.decisionId).fluentPut("name", "决策id").fluentPut("require", true)
                )
            }
            for (def itt = params.iterator(); itt.hasNext(); ) {
                JSONObject jo = itt.next()
                if (!jo.getString("code") || !jo.getString("name")) itt.remove()
                for (def ittt = jo.iterator(); ittt.hasNext(); ) {
                    if (ittt.next().key.startsWith('_')) ittt.remove()
                }
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
    ApiResp addField(HttpContext hCtx, String enName, String cnName, FieldType type, String comment, String dataCollector, String decision) {
        hCtx.auth('field-add')
        if (!enName) return ApiResp.fail("Param enName not empty")
        if (!cnName) return ApiResp.fail("Param cnName not empty")
        if (!type) return ApiResp.fail("Param type not empty")
        String decisionName
        if (decision) {
            def d = repo.findById(Decision, decision)
            if (!d) {
                return ApiResp.fail("Param decision not exist")
            }
            decisionName = d.name
        }
        def field = new RuleField(
            enName: enName, cnName: cnName, type: type, comment: comment, decision: decision == null ? '' : decision,
            dataCollector: dataCollector, creator: hCtx.getSessionAttr('uName')
        )
        try {
            repo.saveOrUpdate(field)
        } catch(ex) {
            def cause = ex
            while (cause != null) {
                if (cause.message.contains("Duplicate entry")) {
                    if (cause.message.contains(RuleField.idx_cnName_decision)) {
                        return ApiResp.fail("$cnName${decision ? ', ' + decisionName + ' ' : ''} aleady exist")
                    }
                    if (cause.message.contains(RuleField.idx_enName_decision)) {
                        return ApiResp.fail("$enName${decision ? ', ' + decisionName + ' ' : ''} aleady exist")
                    }
                }
                cause = cause.cause
            }
            throw ex
        }
        ep.fire('fieldChange', field.id)
        ep.fire('enHistory', field, hCtx.getSessionAttr('uName'))
        ApiResp.ok(field)
    }


    @Path(path = 'addDataCollector', method = 'post')
    ApiResp addDataCollector(
        HttpContext hCtx, String name, String type, String url, String bodyStr,
        String method, String parseScript, String contentType, String comment, String computeScript, String dataSuccessScript,
        String sqlScript, Integer minIdle, Integer maxActive, Integer timeout, Boolean enabled, String cacheKey, String cacheTimeout
    ) {
        hCtx.auth('dataCollector-add')
        DataCollector collector = new DataCollector(name: name, type: type, comment: comment, enabled: (enabled == null ? true : enabled))
        if (!collector.name) return ApiResp.fail('Param name not empty')
        if (!collector.type) return ApiResp.fail('Param type not empty')
        if ('http' == collector.type) {
            if (!url) return ApiResp.fail('Param url not empty')
            if (!method) return ApiResp.fail('Param method not empty')
            if (!contentType && !'get'.equalsIgnoreCase(method)) return ApiResp.fail('Param contentType not empty')
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
        collector.creator = hCtx.getSessionAttr("uName")
        collector.cacheKey = cacheKey
        if (cacheTimeout.trim()) {
            collector.cacheTimeoutFn = '{ -> ' + cacheTimeout.trim() + '}'
        }
        try {
            repo.saveOrUpdate(collector)
        } catch (ex) {
            def cause = ex
            while (cause != null) {
                if (cause.message.contains("Duplicate entry")) {
                    return ApiResp.fail("$name aleady exist")
                }
                cause = cause.cause
            }
            throw ex
        }
        ep.fire('dataCollectorChange', collector.id)
        ep.fire('enHistory', collector, hCtx.getSessionAttr('uName'))
        ApiResp.ok(collector)
    }


    @Path(path = 'updateField', method = 'post')
    ApiResp updateField(HttpContext hCtx, Long id, String enName, String cnName, FieldType type, String comment, String dataCollector, String decision) {
        hCtx.auth('field-update')
        if (!id) return ApiResp.fail("Param id not legal")
        if (!enName) return ApiResp.fail("Param enName not empty")
        if (!cnName) return ApiResp.fail("Param cnName not empty")
        if (!type) return ApiResp.fail("Param type not empty")
        def field = repo.findById(RuleField, id)
        if (field == null) return ApiResp.fail("Param id: $id not found")
        // if (enName != field.enName) return ApiResp.fail('enName can not change')
        String decisionName
        if (decision) {
            def d = repo.findById(Decision, decision)
            if (!d) {
                return ApiResp.fail("Param decision not exist")
            }
            decisionName = d.name
        }
        field.enName = enName
        field.cnName = cnName
        field.type = type
        field.comment = comment
        field.dataCollector = dataCollector
        field.decision = decision == null ? '' : decision
        field.updater = hCtx.getSessionAttr("uName")
        try {
            repo.saveOrUpdate(field)
        } catch (ex) {
            def cause = ex
            while (cause != null) {
                if (cause.message.contains("Duplicate entry")) {
                    if (cause.message.contains(RuleField.idx_cnName_decision)) {
                        return ApiResp.fail("$cnName${decision ? ', ' + decisionName + ' ' : ''} aleady exist")
                    }
                    if (cause.message.contains(RuleField.idx_enName_decision)) {
                        return ApiResp.fail("$enName${decision ? ', ' + decisionName + ' ' : ''} aleady exist")
                    }
                }
                cause = cause.cause
            }
            throw ex
        }
        ep.fire('fieldChange', field.id)
        ep.fire('enHistory', field, hCtx.getSessionAttr('uName'))
        ApiResp.ok(field)
    }


    @Path(path = 'updateDataCollector', method = 'post')
    ApiResp updateDataCollector(
        HttpContext hCtx, String id, String name, String url, String bodyStr,
        String method, String parseScript, String contentType, String comment, String computeScript, String dataSuccessScript,
        String sqlScript, Integer minIdle, Integer maxActive, Integer timeout, Boolean enabled, String cacheKey, String cacheTimeout
    ) {
        hCtx.auth('dataCollector-update')
        if (!id) return ApiResp.fail("Param id not legal")
        if (!name) return ApiResp.fail("Param name not empty")
        def collector = repo.findById(DataCollector, id)
        if (collector == null) return ApiResp.fail("Param id: $id not found")
        if ('http' == collector.type) {
            if (!url) return ApiResp.fail('Param url not empty')
            if (!method) return ApiResp.fail('Param method not empty')
            if ('post'.equalsIgnoreCase(method) && !contentType) {
                return ApiResp.fail('Param contentType not empty')
            }
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
        collector.name = name
        collector.comment = comment
        collector.enabled = enabled == null ? true : enabled
        collector.cacheKey = cacheKey
        if (cacheTimeout.trim()) {
            collector.cacheTimeoutFn = '{ -> ' + cacheTimeout.trim() + '}'
        }
        collector.updater = hCtx.getSessionAttr("uName")

        try {
            repo.saveOrUpdate(collector)
        } catch (ex) {
            def cause = ex
            while (cause != null) {
                if (cause.message.contains("Duplicate entry")) {
                    return ApiResp.fail("$name aleady exist")
                }
                cause = cause.cause
            }
            throw ex
        }
        ep.fire('dataCollectorChange', collector.id)
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


    @Path(path = 'delField/:id')
    ApiResp delField(HttpContext hCtx, Long id) {
        if (!id) return ApiResp.fail("Param id not empty")
        hCtx.auth('field-del')
        def field = repo.findById(RuleField, id)
        repo.delete(field)
        ep.fire('fieldChange', id)
        ep.fire('enHistory', field, hCtx.getSessionAttr('uName'))
        ApiResp.ok()
    }


    @Path(path = 'delDataCollector/:id')
    ApiResp delDataCollector(HttpContext hCtx, String id) {
        if (!id) return ApiResp.fail("Param id not empty")
        hCtx.auth('dataCollector-del')
        def collector = repo.findById(DataCollector, id)
        repo.delete(collector)
        ep.fire('dataCollectorChange', id)
        ep.fire('enHistory', collector, hCtx.getSessionAttr('uName'))
        ApiResp.ok()
    }


    @Path(path = 'testCollector/:id')
    ApiResp testCollector(String id, HttpContext hCtx) {
        ApiResp.ok(fieldManager?.testCollector(id, hCtx.params()))
    }


    @Path(path = 'cleanExpire')
    ApiResp cleanExpire(HttpContext hCtx) {
        hCtx.auth("grant")
        bean(DecisionSrv).cleanDecideRecord()
        return ApiResp.ok().desc("等待后台清理完成...")
    }
}

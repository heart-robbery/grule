package service.rule

import cn.xnatural.app.ServerTpl
import cn.xnatural.app.Utils
import cn.xnatural.enet.event.EC
import cn.xnatural.enet.event.EL
import cn.xnatural.jpa.Repo
import cn.xnatural.remoter.Remoter
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.alibaba.fastjson.serializer.SerializerFeature
import core.OkHttpSrv
import entity.CollectResult
import entity.DataCollector
import entity.FieldType
import entity.RuleField
import groovy.sql.Sql
import groovy.text.GStringTemplateEngine
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

/**
 * 属性管理
 * 属性别名
 * 属性值函数
 */
class AttrManager extends ServerTpl {
    static final String                          DATA_COLLECTED = "data_collected"
    @Lazy def                                    repo           = bean(Repo, 'jpa_rule_repo')
    /**
     * RuleField(enName, cnName), RuleField
     */
    final Map<String, RuleField>                 fieldMap       = new ConcurrentHashMap<>(1000)
    /**
     * 数据获取函数. 收集器名 -> 收集器
     */
    protected final Map<String, CollectorHolder> collectors     = new ConcurrentHashMap(100)


    @EL(name = 'jpa_rule.started', async = true)
    void init() {
        loadField()
        loadDataCollector()

        Long lastWarn // 上次告警时间
        queue(DATA_COLLECTED)
            .failMaxKeep(getInteger(DATA_COLLECTED + ".failMaxKeep", 10000))
            .errorHandle {ex, devourer ->
                if (lastWarn == null || (System.currentTimeMillis() - lastWarn >= Duration.ofSeconds(getLong(DATA_COLLECTED + ".warnInterval", 60 * 5L)).toMillis())) {
                    lastWarn = System.currentTimeMillis()
                    log.error("保存数据收集结果到数据库错误", ex)
                    ep.fire("globalMsg", "保存数据收集结果到数据库错误: " + (ex.message?:ex.class.simpleName))
                }
            }

        ep.fire("${name}.started")
    }


    @EL(name = 'sys.stop', async = true)
    void stop() { collectors.each {it.value.close()} }


    /**
     * 执行数据收集, 获取属性值
     * @param aName 属性名
     * @param ctx
     * @return 当前属性的值
     */
    def dataCollect(String aName, DecisionContext ctx) {
        def field = fieldMap.get(aName)
        if (field == null) {
            log.debug("未找到属性'$aName'对应的配置".toString())
            return
        }
        String collectorName = field.dataCollector // 属性对应的 值 收集器名
        if (!collectorName) {
            log.warn(ctx.logPrefix() + "属性'" + aName + "'没有对应的取值配置")
            return null
        }
        if (ctx.dataCollectResult.containsKey(collectorName)) { // 已查询过
            def value = ctx.dataCollectResult.get(collectorName)
            return value instanceof Map ? value.get(aName) : value
        }

        // 函数执行
        def doApply = {Function<DecisionContext, Object> fn ->
            log.debug(ctx.logPrefix() + "Get attr '{}' value apply function: '{}'", aName, collectorName)
            def v = null
            try {
                v = fn.apply(ctx)
            } catch (ex) { // 接口执行报错, 默认继续往下执行规则
                log.error(ctx.logPrefix() + "数据收集器'$collectorName' 执行错误".toString(), ex)
            }
            if (v instanceof Map) { // 收集器,收集结果为多个属性的值, 则暂先保存
                Map<String, Object> result = new HashMap<>()
                ctx.dataCollectResult.put(collectorName, result)
                v.each {entry ->
                    String k = (String) entry.key
                    result.put(k, entry.value)
                    k = alias(k)
                    if (k) result.put(k, entry.value)
                }
                return result.get(aName)
            }
            else {
                ctx.dataCollectResult.put(collectorName, v)
                return v
            }
        }

        def collector = collectors.get(collectorName)
        if (collector) {
            return doApply(collector.computeFn)
        } else {
            initDataCollector( // 重新去数据库中查找
                repo.find(DataCollector) {root, query, cb -> cb.equal(root.get('enName'), collectorName)}
            )
            collector = collectors.get(collectorName)
            if (collector) return doApply(collector.computeFn)
            else {
                log.warn(ctx.logPrefix() + "Not fund attr '{}' mapped getter function '{}'", aName, collectorName)
                return null
            }
        }
    }


    /**
     * 决策产生的数据接口调用
     * @param collectResult CollectResult
     */
    protected void dataCollected(CollectResult collectResult) {
        queue(DATA_COLLECTED) {
            repo.saveOrUpdate(collectResult)
        }
    }


    /**
     * 得到属性对应的别名
     * @param aName 属性名
     * @return null: 没有别名
     */
    String alias(String aName) {
        def record = fieldMap.get(aName)
        if (record == null) return null
        else if (record.cnName == aName) return record.enName
        else if (record.enName == aName) return record.cnName
        null
    }


    /**
     * 属性值类型转换
     * @param aName 属性名
     * @param aValue 属性值
     * @return 转换后的值
     */
    Object convert(String aName, Object aValue) {
        if (aValue == null) return aValue
        def field = fieldMap.get(aName)
        if (field == null) return aValue
        Utils.to(aValue, field.type.clzType)
    }


    // ======================= 监听变化 ==========================
    @EL(name = ['fieldChange', 'field.dataVersion'], async = true)
    void listenFieldChange(EC ec, String enName) {
        def field = repo.find(RuleField) {root, query, cb -> cb.equal(root.get('enName'), enName)}
        if (field == null) {
            def f = fieldMap.remove(enName)
            if (f) fieldMap.remove(f.cnName)
            log.info("delField: " + f)
        } else {
            boolean isNew = true
            if (fieldMap.containsKey(field.enName)) isNew = false
            fieldMap.put(field.enName, field)
            fieldMap.put(field.cnName, field)
            log.info("${isNew ? 'addField' : 'updateField'}: $enName".toString())
        }
        def remoter = bean(Remoter)
        if (remoter && ec?.source() != remoter) { // 不是远程触发的事件
            remoter.dataVersion('field').update(enName, field ? field.updateTime.time : System.currentTimeMillis(), null)
        }
    }


    @EL(name = ['dataCollectorChange', 'dataCollector.dataVersion'], async = true)
    void listenDataCollectorChange(EC ec, String enName) {
        def collector = repo.find(DataCollector) {root, query, cb -> cb.equal(root.get('enName'), enName)}
        if (collector == null) {
            collectors.remove(enName)?.close()
            log.info("del dataCollector: " + enName)
        } else {
            initDataCollector(collector)
            log.info("dataCollectorChange: " + enName)
        }
        def remoter = bean(Remoter)
        if (remoter && ec?.source() != remoter) { // 不是远程触发的事件
            remoter.dataVersion('dataCollector').update(enName, collector ? collector.updateTime.time : System.currentTimeMillis(), null)
        }
    }


    /**
     * 加载属性
     */
    void loadField() {
        initDefaultAttr()
        Set<String> enNames = (fieldMap.isEmpty() ? null : new HashSet<>(100))
        for (int page = 0, limit = 100; ; page++) {
            def ls = repo.findList(RuleField, page * limit, limit, null)
            if (!ls) break
            ls.each {field ->
                fieldMap.put(field.enName, field)
                fieldMap.put(field.cnName, field)
                enNames?.add(field.enName)
                enNames?.add(field.cnName)
            }
        }
        if (enNames) { // 重新加载, 要删除内存中有, 但库中没有
            fieldMap.findAll {!enNames.contains(it.key)}.each { e ->
                fieldMap.remove(e.key)
            }
        }
        log.info("加载属性配置 {}个", fieldMap.size() / 2)
    }


    /**
     * 初始化默认属性集
     */
    protected void initDefaultAttr() {
        if (repo.count(RuleField) == 0) {
            log.info("初始化默认属性集")
            repo.saveOrUpdate(new RuleField(enName: 'idNumber', cnName: '身份证号码', type: FieldType.Str))
            repo.saveOrUpdate(new RuleField(enName: 'name', cnName: '姓名', type: FieldType.Str))
            repo.saveOrUpdate(new RuleField(enName: 'mobileNo', cnName: '手机号码', type: FieldType.Str))
            repo.saveOrUpdate(new RuleField(enName: 'age', cnName: '年龄', type: FieldType.Int, dataCollector: 'age'))
            repo.saveOrUpdate(new RuleField(enName: 'gender', cnName: '性别', type: FieldType.Str, dataCollector: 'gender', comment: '值: F,M'))
            repo.saveOrUpdate(new RuleField(enName: 'week', cnName: '星期几', type: FieldType.Int, dataCollector: 'week', comment: '值: 1,2,3,4,5,6,7'))
        }
    }


    /**
     * 加载数据集成
     */
    void loadDataCollector() {
        initDefaultCollector()
        Set<String> enNames = (collectors.isEmpty() ? null : new HashSet<>(50))
        for (int page = 0, limit = 100; ; page++) {
            def ls = repo.findList(DataCollector, page * limit, limit, null)
            if (!ls) break
            ls.each {collector ->
                initDataCollector(collector)
                enNames?.add(collector.enName)
            }
        }
        if (enNames) { // 重新加载, 要删除内存中有, 但库中没有
            collectors.findAll {!enNames.contains(it.key)}.each { e ->
                collectors.remove(e.key)
            }
        }
        log.info("加载数据集成配置 {}个", collectors.size())
    }


    /**
     * 初始化默认数据集
     */
    protected void initDefaultCollector() {
        if (repo.count(DataCollector) == 0) {
            log.info("初始化默认数据集")
            repo.saveOrUpdate(new DataCollector(type: 'script', enName: 'week', cnName: '星期几', enabled: true, comment: '值: 1,2,3,4,5,6,7', computeScript: """
Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
            """.trim()))
            repo.saveOrUpdate(new DataCollector(type: 'script', enName: 'gender', cnName: '性别', enabled: true, comment: '根据身份证计算. 值: F,M', computeScript: """
if (idNumber && idNumber.length() > 17) {
    Integer.parseInt(idNumber.substring(16, 17)) % 2 == 0 ? 'F' : 'M'
} else null
            """.trim()))
            repo.saveOrUpdate(new DataCollector(type: 'script', enName: 'age', cnName: '年龄', enabled: true, comment: '根据身份证计算', computeScript: """
if (idNumber && idNumber.length() > 17) {
    Calendar cal = Calendar.getInstance()
    int yearNow = cal.get(Calendar.YEAR)
    int monthNow = cal.get(Calendar.MONTH) + 1
    int dayNow = cal.get(Calendar.DATE)

    int birthday = Integer.valueOf(idNumber.substring(6, 14))
    int year = Integer.valueOf(idNumber.substring(6, 10))
    int month = Integer.valueOf(idNumber.substring(10,12))
    int day = Integer.valueOf(idNumber.substring(12,14))
    
    if ((month < monthNow) || (month == monthNow && day <= dayNow)) {
        yearNow - year
    } else {
        yearNow - year - 1
    }
} else null
            """.trim()))
        }
    }


    /**
     * 初始全数据收集器
     * @param collector
     */
    void initDataCollector(DataCollector collector) {
        if (!collector) return
        if ('http' == collector.type) { // http 接口
            initHttpCollector(collector)
        } else if ('script' == collector.type) { // groovy 脚本
            initScriptCollector(collector)
        } else if ('sql' == collector.type) { // 数据库查询脚本
            initSqlCollector(collector)
        } else throw new Exception("Not support type: $collector.type")
    }


    /**
     * 初始化 sql 收集器
     * @param collector
     */
    protected void initSqlCollector(DataCollector collector) {
        if ('sql' != collector.type) return
        if (!collector.enabled) {
            collectors.remove(collector.enName)?.close(); return
        }
        if (!collector.url) {
            log.warn('sql url must not be empty'); return
        }
        if (!collector.sqlScript) {
            log.warn('sqlScript must not be empty'); return
        }
        if (collector.minIdle < 0 || collector.minIdle > 50) {
            log.warn('0 <= minIdle <= 50'); return
        }
        if (collector.maxActive < 1 || collector.maxActive > 100) {
            log.warn('1 <= minIdle <= 100'); return
        }

        def db = new Sql(Repo.createDataSource([ //创建一个DB. 用于界面配置sql脚本
            url: collector.url, jdbcUrl: collector.url,
            minIdle: collector.minIdle, maxActive: collector.maxActive,
            minimumIdle: collector.minIdle, maximumPoolSize: collector.maxActive
        ]))

        Binding binding = new Binding()
        def config = new CompilerConfiguration()
        binding.setProperty('DB', db)
        def icz = new ImportCustomizer()
        config.addCompilationCustomizers(icz)
        icz.addImports(JSON.class.name, JSONObject.class.name, Utils.class.name)
        Closure sqlScript = new GroovyShell(Thread.currentThread().contextClassLoader, binding, config).evaluate("{ -> $collector.sqlScript }")

        collectors.remove(collector.enName)?.close()
        collectors.put(collector.enName, new CollectorHolder(collector: collector, sql: db, computeFn: { ctx ->
            def start = new Date()
            Object result
            Exception exx
            try {
                result = sqlScript.rehydrate(ctx.data, sqlScript, this)()
                log.info(ctx.logPrefix() + "Sql脚本函数'$collector.enName'执行结果: $result".toString())
            } catch (ex) {
                exx = ex
                log.error(ctx.logPrefix() + "Sql脚本函数'$collector.enName'执行失败".toString(), ex)
            }
            dataCollected(new CollectResult(
                decideId: ctx.id, decisionId: ctx.decisionHolder.decision.decisionId, collector: collector.enName,
                status: (exx ? 'EEEE' : '0000'), collectDate: start, collectorType: collector.type,
                spend: System.currentTimeMillis() - start.time,
                result: result instanceof Map ? JSON.toJSONString(result, SerializerFeature.WriteMapNullValue) : result?.toString(),
                scriptException: exx == null ? null : exx.message?:exx.class.simpleName
            ))
            return result
        }))
    }


    /**
     * 初始化 script 收集器
     * @param collector
     */
    protected void initScriptCollector(DataCollector collector) {
        if ('script' != collector.type) return
        if (!collector.enabled) {
            collectors.remove(collector.enName)?.close(); return
        }
        if (!collector.computeScript) {
            log.warn("Script collector'$collector.enName' script must not be empty".toString()); return
        }
        Binding binding = new Binding()
        def config = new CompilerConfiguration()
        def icz = new ImportCustomizer()
        config.addCompilationCustomizers(icz)
        icz.addImports(JSON.class.name, JSONObject.class.name, Utils.class.name)
        Closure script = new GroovyShell(Thread.currentThread().contextClassLoader, binding, config).evaluate("{ -> $collector.computeScript}")
        collectors.put(collector.enName, new CollectorHolder(collector: collector, computeFn: { ctx ->
            Object result
            final def start = new Date()
            Exception ex
            try {
                result = script.rehydrate(ctx.data, script, this)()
                log.info(ctx.logPrefix() + "脚本函数'$collector.enName'执行结果: $result".toString())
            } catch (e) {
                ex = e
                log.error(ctx.logPrefix() + "脚本函数'$collector.enName'执行失败".toString(), ex)
            }
            dataCollected(new CollectResult(
                decideId: ctx.id, decisionId: ctx.decisionHolder.decision.decisionId, collector: collector.enName,
                status: (ex ? 'EEEE' : '0000'), collectDate: start, collectorType: collector.type,
                spend: System.currentTimeMillis() - start.time,
                result: result instanceof Map ? JSON.toJSONString(result, SerializerFeature.WriteMapNullValue) : result?.toString(),
                scriptException: ex == null ? null : ex.message?:ex.class.simpleName
            ))
            return result
        }))
    }


    /**
     * 初始化 http 收集器
     * @param collector
     */
    protected void initHttpCollector(DataCollector collector) {
        if ('http' != collector.type) return
        if (!collector.enabled) {
            collectors.remove(collector.enName)?.close(); return
        }
        // 创建 http 客户端
        def http = new OkHttpSrv('okHttp_' + collector.enName); app().inject(http)
        http.setAttr('connectTimeout', getLong("http.connectTimeout." + collector.enName, getLong('http.connectTimeout', 3L)))
        http.setAttr('readTimeout', getLong("http.readTimeout." + collector.enName, getLong('http.readTimeout', Long.valueOf(collector.timeout?:20))))
        http.init()

        Closure parseFn
        if (collector.parseScript) { // 结果解析函数
            Binding binding = new Binding()
            def config = new CompilerConfiguration()
            def icz = new ImportCustomizer()
            config.addCompilationCustomizers(icz)
            icz.addImports(JSON.class.name, JSONObject.class.name, Utils.class.name)
            parseFn = new GroovyShell(Thread.currentThread().contextClassLoader, binding, config).evaluate("$collector.parseScript")
        }
        // GString 模板替换
        def tplEngine = new GStringTemplateEngine(Thread.currentThread().contextClassLoader)
        collectors.put(collector.enName, new CollectorHolder(collector: collector, computeFn: { ctx -> // 数据集成中3方接口访问过程
            // http请求 url
            String url = collector.url
            for (int i = 0; i < 2; i++) { // 替换 ${} 变量
                if (!url.contains('${')) break
                url = tplEngine.createTemplate(url).make(new HashMap(1) {
                    int paramIndex
                    @Override
                    boolean containsKey(Object key) { return true } // 加这行是为了 防止 MissingPropertyException
                    @Override
                    Object get(Object key) {
                        paramIndex++ // 获取个数记录
                        def v = ctx.data.get(key)
                        // url前缀不必编码, 其它参数需要编码
                        return v == null ? '' : (paramIndex==1 && v.toString().startsWith("http") && collector.url.startsWith('${') ? v : URLEncoder.encode(v.toString(), 'utf-8'))
                    }

                    @Override
                    Object put(Object key, Object value) {
                        log.error("$collector.enName url config error, not allow set property '$key'".toString())
                        null
                    }
                }).toString()
            }

            // http 请求 body字符串
            String bodyStr = collector.bodyStr
            for (int i = 0; i < 2; i++) { // 替换 ${} 变量
                if (!bodyStr.contains('${')) break
                bodyStr = collector.bodyStr ? tplEngine.createTemplate(bodyStr).make(new HashMap(1) {
                    @Override
                    boolean containsKey(Object key) { return true } // 加这行是为了 防止 MissingPropertyException
                    @Override
                    Object get(Object key) { ctx.data.get(key)?:"" }
                    @Override
                    Object put(Object key, Object value) {
                        log.error("$collector.enName bodyStr config error, not allow set property '$key'".toString())
                        null
                    }
                }).toString() : ''
            }
            // NOTE: 如果是json 并且是,} 结尾, 则删除 最后的,(因为spring解析入参数会认为json格式错误)
            // if (bodyStr.endsWith(',}')) bodyStr = bodyStr.substring(0, bodyStr.length() - 3) + '}'

            String result // 接口返回结果字符串
            Object resolveResult // 解析接口返回结果

            final Date start = new Date() // 调用时间
            long spend = 0 // 耗时时长
            String retryMsg = ''
            def logMsg = "${ctx.logPrefix()}接口调用${ -> retryMsg}: name: $collector.enName, url: $url, bodyStr: $bodyStr${ -> ', result: ' + result}${ -> ', resolveResult: ' + resolveResult}"
            try {
                for (int i = 0, times = getInteger('http.retry', 2) + 1; i < times; i++) { // 接口一般遇网络错重试2次
                    try {
                        retryMsg = i > 0 ? "(重试第${i}次)" : ''
                        if ('get'.equalsIgnoreCase(collector.method)) {
                            result = http.get(url).execute()
                        } else if ('post'.equalsIgnoreCase(collector.method)) {
                            result = http.post(url).textBody(bodyStr).contentType(collector.contentType).execute()
                        } else throw new Exception("Not support http method $collector.method")
                        break
                    } catch (ex) {
                        if ((ex instanceof ConnectException) && (i + 1) < times) {
                            log.error(logMsg.toString() + ", 异常: " + (ex.class.simpleName + ': ' + ex.message))
                            continue
                        } else throw ex
                    } finally {
                        spend = System.currentTimeMillis() - start.time
                    }
                }
            } catch (ex) {
                log.error(logMsg.toString() + ", 异常: ", ex)
                dataCollected(new CollectResult(
                    decideId: ctx.id, decisionId: ctx.decisionHolder.decision.decisionId, collector: collector.enName,
                    status: (ex instanceof ConnectException) ? 'E001': 'EEEE',
                    collectDate: start, collectorType: collector.type,
                    spend: spend, url: url, body: bodyStr, result: result, httpException: ex.message?:ex.class.simpleName
                ))
                return result
            } finally {
                retryMsg = ''
            }

            if (parseFn) { // 解析接口返回结果
                Exception ex
                try {
                    resolveResult = parseFn.rehydrate(ctx.data, parseFn, this)(result)
                } catch (e) {
                    ex = e
                } finally {
                    if (ex) {
                        log.error(logMsg.toString() + ", 解析函数执行失败", ex)
                    } else {
                        log.info(logMsg.toString())
                    }
                    dataCollected(new CollectResult(
                        decideId: ctx.id, decisionId: ctx.decisionHolder.decision.decisionId, collector: collector.enName,
                        status: ex ? 'E002': '0000', collectDate: start, collectorType: collector.type,
                        spend: spend, url: url, body: bodyStr, result: result, parseException: ex == null ? null : ex.message?:ex.class.simpleName,
                        resolveResult: resolveResult instanceof Map ? JSON.toJSONString(resolveResult, SerializerFeature.WriteMapNullValue) : resolveResult?.toString()
                    ))
                }
                return resolveResult
            }
            log.info(logMsg.toString())
            dataCollected(new CollectResult(
                decideId: ctx.id, decisionId: ctx.decisionHolder.decision.decisionId, collector: collector.enName,
                status: '0000', collectDate: start, collectorType: collector.type,
                spend: spend, url: url, body: bodyStr, result: result
            ))
            return result
        }))
    }


    /**
     * 收集器 Holder
     */
    class CollectorHolder {
        // 对应数据库中的实体
        DataCollector collector
        // 把收集器转换的执行函数
        Function<DecisionContext, Object> computeFn
        // DB
        Sql sql

        void close() {
            try {
                sql?.dataSource?.invokeMethod('close', null)
                sql?.close()
            } catch (ex) {}
        }
    }
}
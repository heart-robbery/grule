package service.rule

import cn.xnatural.enet.event.EC
import cn.xnatural.enet.event.EL
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.alibaba.fastjson.serializer.SerializerFeature
import core.OkHttpSrv
import core.ServerTpl
import core.Utils
import core.jpa.BaseRepo
import core.jpa.HibernateSrv
import dao.entity.CollectResult
import dao.entity.DataCollector
import dao.entity.FieldType
import dao.entity.RuleField
import groovy.sql.Sql
import groovy.text.GStringTemplateEngine
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

/**
 * 属性管理
 * 属性别名
 * 属性值函数
 */
class AttrManager extends ServerTpl {

    @Lazy def                                                      repo             = bean(BaseRepo, 'jpa_rule_repo')
    /**
     * RuleField(enName, cnName), RuleField
     */
    final Map<String, RuleField>                                   attrMap          = new ConcurrentHashMap<>(1000)
    /**
     * 数据获取函数. 函数名 -> 函数
     */
    protected final Map<String, Function<DecisionContext, Object>> dataCollectorMap = new ConcurrentHashMap()


    @EL(name = 'jpa_rule.started', async = true)
    void init() {
        // 有顺序
        loadField()
        loadDataCollector()
        ep.fire("${name}.started")
    }


    /**
     * 执行数据收集, 获取属性值
     * @param aName 属性名
     * @param ctx
     * @return 当前属性的值
     */
    def dataCollect(String aName, DecisionContext ctx) {
        def field = attrMap.get(aName)
        if (field == null) {
            log.warn("未找到属性'$aName'对应的配置")
            return
        }
        String collectorName = field.dataCollector // 属性对应的 值 收集器名
        if (!collectorName) {
            log.warn(ctx.logPrefix() + "属性'" + aName + "'没有对应的取值配置")
            return null
        }
        if (ctx.dataCollectResult.containsKey(collectorName)) { // 已查询过
            return ctx.dataCollectResult.get(collectorName).get(aName)
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
            else return v
        }

        def fn = dataCollectorMap.get(collectorName)
        if (fn) {
            return doApply(fn)
        } else {
            initDataCollect( // 重新去数据库中查找
                repo.find(DataCollector) {root, query, cb -> cb.equal(root.get('enName'), collectorName)}
            )
            fn = dataCollectorMap.get(collectorName)
            if (fn) return doApply(fn)
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
    // @EL(name = 'decision.dataCollected', async = true)
    void dataCollected(CollectResult collectResult) {
        repo.saveOrUpdate(collectResult)
    }


    /**
     * 数据收集器 设置
     * @param collectorName 收集器名
     * @param collector 收集器函数
     */
    Function<DecisionContext, Object> setCollector(String collectorName, Function<DecisionContext, Object> collector) { dataCollectorMap.put(collectorName, collector) }


    /**
     * 得到属性对应的别名
     * @param aName 属性名
     * @return null: 没有别名
     */
    String alias(String aName) {
        def record = attrMap.get(aName)
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
        def field = attrMap.get(aName)
        if (field == null) return aValue
        Utils.to(aValue, field.type.clzType)
    }


    /**
     * 加载属性
     */
    void loadField() {
        if (!attrMap.isEmpty()) attrMap.clear()
        initAttr()
        log.info("加载属性配置")
        int page = 1
        do {
            def p = repo.findPage(RuleField, (page++), 100)
            if (!p.list) break
            p.list.each {field ->
                attrMap.put(field.enName, field)
                attrMap.put(field.cnName, field)
            }

        } while (true)
    }


    // ======================= 监听变化 ==========================
    @EL(name = 'addField', async = true)
    void listenFieldAdd(String enName) {
        def field = repo.find(RuleField) {root, query, cb -> cb.equal(root.get('enName'), enName)}
        attrMap.put(field.enName, field)
        attrMap.put(field.cnName, field)
        log.info("addField: " + enName)
    }
    @EL(name = 'updateField', async = true)
    void listenFieldUpdate(String enName) {
        def field = repo.find(RuleField) {root, query, cb -> cb.equal(root.get('enName'), enName)}
        attrMap.put(field.enName, field)
        attrMap.put(field.cnName, field)
        log.info("updateField: " + enName)
    }
    @EL(name = 'delField')
    void listenDelField(String enName) {
        def field = attrMap.remove(enName)
        attrMap.remove(field.cnName)
        ep.fire('remote', EC.of(this).attr('toAll', true).args(app.name, 'delField', [enName]))
        log.info("delField: " + enName)
    }
    @EL(name = 'addDataCollector', async = true)
    void listenAddDataCollector(String enName) {
        def collector = repo.find(DataCollector) {root, query, cb -> cb.equal(root.get('enName'), enName)}
        initDataCollect(collector)
        log.info("addDataCollector: " + enName)
    }
    @EL(name = 'updateDataCollector', async = true)
    void listenUpdateDataCollector(String enName) {
        def collector = repo.find(DataCollector) {root, query, cb -> cb.equal(root.get('enName'), enName)}
        initDataCollect(collector)
        loadField()
        log.info("addDataCollector: " + enName)
    }
    @EL(name = 'delDataCollector', async = true)
    void listenDelCollector(String enName) {
        dataCollectorMap.remove(enName)
        log.info("delDataCollector: " + enName)
    }



    /**
     * 初始化默认属性集
     */
    protected void initAttr() {
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
        if (!dataCollectorMap.isEmpty()) dataCollectorMap.clear()
        initDataCollector()
        log.info("加载数据集成配置")
        repo.findList(DataCollector).each {initDataCollect(it)}
    }


    /**
     * 初始化默认数据集
     */
    protected void initDataCollector() {
        if (repo.count(DataCollector) == 0) {
            log.info("初始化默认数据集")
            repo.saveOrUpdate(new DataCollector(type: 'script', enName: 'week', cnName: '星期几', comment: '值: 1,2,3,4,5,6,7', computeScript: """
Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
            """.trim()))
            repo.saveOrUpdate(new DataCollector(type: 'script', enName: 'gender', cnName: '性别', comment: '根据身份证计算. 值: F,M', computeScript: """
if (idNumber && idNumber.length() > 17) {
    Integer.parseInt(idNumber.substring(16, 17)) % 2 == 0 ? 'F' : 'M'
} else null
            """.trim()))
            repo.saveOrUpdate(new DataCollector(type: 'script', enName: 'age', cnName: '年龄', comment: '根据身份证计算', computeScript: """
if (idNumber && idNumber.length() > 17) {
    Calendar cal = Calendar.getInstance()
    int yearNow = cal.get(Calendar.YEAR)
    int monthNow = cal.get(Calendar.MONTH)+1
    int dayNow = cal.get(Calendar.DATE)

    int birthday = Integer.valueOf(idNumber.substring(6, 14))
    int year = Integer.valueOf(idNumber.substring(6, 10))
    int month = Integer.valueOf(idNumber.substring(10,12))
    int day = Integer.valueOf(idNumber.substring(12,14))
    
    if ((month < monthNow) || (month == monthNow && day <= dayNow)){
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
    void initDataCollect(DataCollector collector) {
        if (!collector) return
        if ('http' == collector.type) { // http 接口
            initHttpCollector(collector)
        } else if ('script' == collector.type) {
            initScriptCollector(collector)
        } else if ('sql' == collector.type) {
            initSqlCollector(collector)
        } else throw new Exception("Not support type: $collector.type")
    }


    /**
     * 初始化 sql 收集器
     * @param collector
     */
    protected void initSqlCollector(DataCollector collector) {
        if (!collector.sqlScript) {
            log.warn('sqlScript must not be empty')
            return
        }
        if (collector.minIdle < 0 || collector.minIdle > 50) {
            log.warn('0 <= minIdle <= 50')
            return
        }
        if (collector.maxActive < 1 || collector.maxActive > 100) {
            log.warn('1 <= minIdle <= 100')
            return
        }

        def db = new Sql(HibernateSrv.createDs([
            minIdle: collector.minIdle, maxActive: collector.maxActive
        ]))

        Binding binding = new Binding()
        def config = new CompilerConfiguration()
        binding.setProperty('DB', db)
        def icz = new ImportCustomizer()
        config.addCompilationCustomizers(icz)
        icz.addImports(JSON.class.name, JSONObject.class.name)
        Closure sqlScript = new GroovyShell(Thread.currentThread().contextClassLoader, binding, config).evaluate("{ -> $collector.sqlScript }")

        setCollector(collector.enName) {ctx ->
            sqlScript.rehydrate(ctx.data, sqlScript, this)()
        }
    }


    /**
     * 初始化 script 收集器
     * @param collector
     */
    protected void initScriptCollector(DataCollector collector) {
        if (!collector.computeScript) {
            log.warn("Script collector'$collector.enName' script must not be empty".toString())
            return
        }
        Binding binding = new Binding()
        def config = new CompilerConfiguration()
        def icz = new ImportCustomizer()
        config.addCompilationCustomizers(icz)
        icz.addImports(JSON.class.name, JSONObject.class.name)
        Closure script = new GroovyShell(Thread.currentThread().contextClassLoader, binding, config).evaluate("{ -> $collector.computeScript}")
        setCollector(collector.enName) { ctx ->
            Object result
            final def start = new Date()
            Exception exx
            try {
                result = script.rehydrate(ctx.data, script, this)()
            } catch (ex) {
                exx = ex
                log.error(ctx.logPrefix() + "脚本函数'$collector.enName'执行失败".toString(), ex)
            } finally {
                log.info(ctx.logPrefix() + "脚本函数'$collector.enName'执行结果: $result".toString())
                // TODO 保存吗
                dataCollected(new CollectResult(
                    decideId: ctx.id, decisionId: ctx.decisionSpec.决策id, collector: collector.enName,
                    collectDate: start, collectorType: collector.type,
                    spend: System.currentTimeMillis() - start.time,
                    result: result instanceof Map ? JSON.toJSONString(result, SerializerFeature.WriteMapNullValue) : result?.toString(),
                    scriptException: exx == null ? null : exx.message?:exx.class.simpleName
                ))
            }
            return result
        }
    }


    /**
     * 初始化 http 收集器
     * @param collector
     */
    protected void initHttpCollector(DataCollector collector) {
        def http = bean(OkHttpSrv)
        Closure parseFn
        if (collector.parseScript) {
            Binding binding = new Binding()
            def config = new CompilerConfiguration()
            def icz = new ImportCustomizer()
            config.addCompilationCustomizers(icz)
            icz.addImports(JSON.class.name, JSONObject.class.name)
            parseFn = new GroovyShell(Thread.currentThread().contextClassLoader, binding, config).evaluate("$collector.parseScript")
        }
        // GString 模板替换
        def tplEngine = new GStringTemplateEngine(Thread.currentThread().contextClassLoader)
        setCollector(collector.enName) { ctx -> // 数据集成中3方接口访问过程
            // http请求 url
            String url = tplEngine.createTemplate(collector.url).make(new HashMap(1) {
                @Override
                Object get(Object key) {
                    def v = ctx.data.get(key)
                    return v == null ? '' : URLEncoder.encode(v.toString(), 'utf-8')
                }
            }).toString()
            // http 请求 body字符串
            String bodyStr = collector.bodyStr ? tplEngine.createTemplate(collector.bodyStr).make(new HashMap(1) {
                @Override
                Object get(Object key) {
                    def v = ctx.data.get(key)
                    return v == null ? '' : URLEncoder.encode(v.toString(), 'utf-8')
                }
            }).toString() : ''
            String result // 接口返回结果字符串
            Object resolveResult // 解析接口返回结果

            final Date start = new Date() // 调用时间
            long spend = 0 // 耗时时长
            String retryMsg = ''
            def logMsg = "${ctx.logPrefix()}接口调用${ -> retryMsg}: name: $collector.enName, url: $url, bodyStr: $bodyStr${ -> ', result: ' + result}${ -> ', resolveResult: ' + resolveResult}"
            try {
                for (int i = 0, times = getInteger('http.retry', 2) + 1; i < times; i++) { // 接口一般遇网络错重试3次
                    try {
                        retryMsg = i > 0 ? "(重试第${i}次)" : ''
                        if ('get'.equalsIgnoreCase(collector.method)) {
                            result = http.get(url).execute()
                        } else if ('post'.equalsIgnoreCase(collector.method)) {
                            result = http.post(url).textBody(bodyStr).contentType(collector.contentType).execute()
                        } else throw new Exception("Not support http method $collector.method")
                        break
                    } catch (ex) {
                        if ((ex instanceof SocketTimeoutException || ex instanceof ConnectException) && (i + 1) < times) {
                            log.error(logMsg.toString() + ". " + (ex.class.simpleName + ': ' + ex.message))
                            continue
                        } else throw ex
                    } finally {
                        spend = System.currentTimeMillis() - start.time
                    }
                }
            } catch (ex) {
                log.error(logMsg.toString(), ex)
                dataCollected(new CollectResult(
                    decideId: ctx.id, decisionId: ctx.decisionSpec.决策id, collector: collector.enName,
                    collectDate: start, collectorType: collector.type,
                    spend: spend, url: url, body: bodyStr, result: result, httpException: ex.message?:ex.class.simpleName
                ))
                return result
            } finally {
                retryMsg = ''
            }

            if (parseFn) { // 如果有配置解析函数,则对接口返回结果解析
                Exception exx
                try {
                    resolveResult = parseFn.rehydrate(ctx.data, parseFn, this)(result)
                } catch (ex) {
                    exx = ex
                } finally {
                    if (exx) {
                        log.error(logMsg.toString() + ", 解析函数执行失败", exx)
                    } else {
                        log.info(logMsg.toString())
                    }
                    dataCollected(new CollectResult(
                        decideId: ctx.id, decisionId: ctx.decisionSpec.决策id, collector: collector.enName,
                        collectDate: start, collectorType: collector.type,
                        spend: spend, url: url, body: bodyStr, result: result, parseException: exx == null ? null : exx.message?:exx.class.simpleName,
                        resolveResult: resolveResult instanceof Map ? JSON.toJSONString(resolveResult, SerializerFeature.WriteMapNullValue) : resolveResult?.toString()
                    ))
                }
                return resolveResult
            }
            log.info(logMsg.toString())
            dataCollected(new CollectResult(
                decideId: ctx.id, decisionId: ctx.decisionSpec.决策id, collector: collector.enName,
                collectDate: start, collectorType: collector.type,
                spend: spend, url: url, body: bodyStr, result: result
            ))
            return result
        }
    }
}
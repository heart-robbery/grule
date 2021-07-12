package service.rule

import cn.xnatural.app.CacheSrv
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
import core.RedisClient
import entity.CollectRecord
import entity.DataCollector
import groovy.sql.Sql
import groovy.text.GStringTemplateEngine
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.slf4j.LoggerFactory

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function

/**
 * 收集器
 */
class CollectorManager extends ServerTpl {
    protected static final String DATA_COLLECTED = "data_collected"
    @Lazy def repo = bean(Repo, 'jpa_rule_repo')
    @Lazy def redis = bean(RedisClient)
    @Lazy def cacheSrv = bean(CacheSrv)
    @Lazy def fieldManager = bean(FieldManager)
    /**
     * 数据获取函数. 收集器id -> 收集器
     */
    final Map<String, CollectorHolder> collectorHolders = new ConcurrentHashMap(100)
    /**
     * 决策id -> 保存延迟计算的收集器记录函数
     */
    protected final Map<String, List<Closure>> lazyCollectRecords = new ConcurrentHashMap<>()


    @EL(name = 'jpa_rule.started', async = true)
    void init() {
        load()

        Long lastWarn // 上次告警时间
        queue(DATA_COLLECTED)
                .failMaxKeep(getInteger(DATA_COLLECTED + ".failMaxKeep", 10000))
                .parallel(getInteger("saveResult.parallel", 10))
                .errorHandle {ex, me ->
                    if (lastWarn == null || (System.currentTimeMillis() - lastWarn >= Duration.ofSeconds(getLong(DATA_COLLECTED + ".warnInterval", 60 * 5L)).toMillis())) {
                        lastWarn = System.currentTimeMillis()
                        log.error("保存数据收集结果到数据库错误", ex)
                        ep.fire("globalMsg", "保存数据收集结果到数据库错误: " + (ex.message?:ex.class.simpleName))
                    }
                    // 暂停一会
                    me.suspend(Duration.ofMillis(500 + new Random().nextInt(1000)))
                }

        ep.fire("${name}.started")
    }


    @EL(name = 'sys.stopping', async = true)
    protected void stop() {
        collectorHolders.each {it.value.close()}
        // 尽量等到 对列中的 数据都持久化完成
        long start = System.currentTimeMillis()
        if (queue(DATA_COLLECTED).waitingCount > 0) log.warn("等待数据收集结果数据保存完...")
        while (queue(DATA_COLLECTED).waitingCount > 0 && System.currentTimeMillis() - start < 1000 * 60 * 2) {
            Thread.sleep(1000)
        }
    }


    @EL(name = 'decision.end', async = true)
    void endDecision(DecisionContext ctx) { lazyCollectRecords.remove(ctx.id)?.each { it.call() } }


    @EL(name = ['dataCollectorChange', 'dataCollector.dataVersion'], async = true)
    protected void listenDataCollectorChange(EC ec, String id) {
        def collector = repo.findById(DataCollector, id)
        if (collector == null) {
            collectorHolders.remove(id)?.close()
            log.info("del dataCollector: " + id)
        } else {
            log.info("dataCollectorChanged: " + collector.name + ", " + id)
            initDataCollector(collector)
        }
        def remoter = bean(Remoter)
        if (remoter && ec?.source() != remoter) { // 不是远程触发的事件
            remoter.dataVersion('dataCollector').update(id, collector ? collector.updateTime.time : System.currentTimeMillis(), null)
        }
    }


    def populate(String collectorId, String aName, DecisionContext ctx) {
        // 得到收集器
        def collectorHolder = collectorHolders.get(collectorId)
        if (collectorHolder == null) {
            // 重新去数据库中查找
            initDataCollector(repo.findById(DataCollector, collectorId))
            collectorHolder = collectorHolders.get(collectorId)
        }
        // 未找到收集器
        if (!collectorHolder) {
            log.warn(ctx.logPrefix() + "Not fund '${aName}' mapped getter function '${collectorHolder.collector.name}($collectorId)'".toString())
            return null
        }

        return collectorHolder.populate(aName, ctx)
    }


    /**
     * 测试 收集器
     * @param id 收集器id
     * @param param 参数
     */
    def testCollector(String id, Map param) { collectorHolders.get(id)?.testComputeFn?.apply(param) }


    // 决策产生的数据收集
    protected void dataCollected(
            DataCollector collector, DecisionContext ctx, Boolean cache, Date collectDate, Long spend, String dataStatus,
            Object result, Exception ex, def resolveResult, Exception resolveException, String url = null, String body = null
    ) {
        queue(DATA_COLLECTED) {
            // 是否有延迟计算值
            boolean hasLazyValue
            def resolveResultStrFn = {
                if (resolveResult instanceof Map) {
                    return JSON.toJSONString(
                            resolveResult.findAll {e ->
                                if (e.value instanceof Closure) {
                                    hasLazyValue = true
                                    return false
                                }
                                else true
                            },
                            SerializerFeature.WriteMapNullValue
                    )
                } else {
                    return resolveResult?.toString()
                }
            }

            String status = '0000'
            if (ex instanceof ConnectException) status = 'E001'
            else if (ex != null) status = 'EEEE'
            else if (resolveException != null) status = 'E002'
            def record = new CollectRecord(
                    decideId: ctx.id, decisionId: ctx.decisionHolder.decision.id, collector: collector.id,
                    status: status, dataStatus: dataStatus,
                    collectDate: collectDate, cache: cache, spend: spend, url: url, body: body,
                    result: result, exception: ex == null ? null : ex.message?:ex.class.simpleName,
                    resolveResult: resolveResultStrFn(),
                    resolveException: resolveException == null ? null : resolveException.message?:resolveException.class.simpleName
            )
            if (hasLazyValue) { // 如果有延迟计算值, 则在决策结束后更新收集器结果
                lazyCollectRecords.computeIfAbsent(ctx.id, { new LinkedList<>() }).add({
                    record.resolveResult = resolveResultStrFn()
                    repo.saveOrUpdate(record)
                })
            } else {
                repo.saveOrUpdate(record)
            }
        }
    }


    /**
     * 加载所有数据收集器
     */
    void load() {
        initDefaultCollector()
        final Set<String> ids = (collectorHolders ? ConcurrentHashMap.newKeySet(collectorHolders.size()) : null)
        final def threshold = new AtomicInteger(1)
        final def tryComplete = {
            if (threshold.decrementAndGet() > 0) return
            if (ids) { // 重新加载, 要删除内存中有, 但库中没有
                collectorHolders.findAll {!ids.contains(it.key)}.each { e ->
                    collectorHolders.remove(e.key)
                }
            }
            log.info("加载数据收集器 {}个", collectorHolders.size())
        }
        for (int page = 0, limit = 20; ; page++) {
            def ls = repo.findList(DataCollector, page * limit, limit)
            threshold.incrementAndGet()
            async {
                ls.each {collector ->
                    initDataCollector(collector)
                    ids?.add(collector.id)
                }
                tryComplete()
            }
            if (!ls || ls.size() < limit) {
                tryComplete(); break
            }
        }
    }


    /**
     * 初始化默认数据集
     */
    protected void initDefaultCollector() {
        if (repo.count(DataCollector) == 0) {
            log.info("初始化默认数据收集器")
            repo.saveOrUpdate(new DataCollector(type: 'script', name: '星期几', enabled: true, comment: '值: 1,2,3,4,5,6,7', computeScript: """
def week = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
if (week == 1) 7
else week - 1
            """.trim()))

            repo.saveOrUpdate(new DataCollector(type: 'script', name: '当前日期时间', enabled: true, comment: '值: yyyy-MM-dd HH:mm:ss', cacheKey: '${(long) (System.currentTimeMillis() / 1000)}', computeScript: """
new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
            """.trim()))
            repo.saveOrUpdate(new DataCollector(type: 'script', name: '当前日期', enabled: true, comment: '值: yyyy-MM-dd', cacheKey: '${(long) (System.currentTimeMillis() / (1000 * 60 * 60 * 24))}', computeScript: """
new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date())
            """.trim()))
            repo.saveOrUpdate(new DataCollector(type: 'script', name: '当前时间', enabled: true, comment: '值: HH:mm:ss', cacheKey: '${(long) (System.currentTimeMillis() / 1000)}', computeScript: """
new java.text.SimpleDateFormat("HH:mm:ss").format(new Date())
            """.trim()))

            repo.saveOrUpdate(new DataCollector(type: 'script', name: '性别', enabled: true, comment: '根据身份证计算. 值: F,M', computeScript: """
if (idNumber && idNumber.length() > 17) {
    Integer.parseInt(idNumber.substring(16, 17)) % 2 == 0 ? 'F' : 'M'
} else null
            """.trim()))

            repo.saveOrUpdate(new DataCollector(type: 'script', name: '年龄', enabled: true, comment: '根据身份证计算', computeScript: """
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
     */
    void initDataCollector(DataCollector collector) {
        if (!collector) return
        try {
            collectorHolders.remove(collector.id)?.close()
            if (!collector.enabled) return
            if ('http' == collector.type) { // http 接口
                initHttpCollector(collector)
            } else if ('script' == collector.type) { // groovy 脚本
                initScriptCollector(collector)
            } else if ('sql' == collector.type) { // 数据库查询脚本
                initSqlCollector(collector)
            } else throw new Exception("Not support type: $collector.type")
        } catch (ex) {
            log.error("初始化收集器'$collector.name($collector.id)' 错误".toString(), ex)
        }
    }


    /**
     * 初始化 sql 收集器
     */
    protected void initSqlCollector(DataCollector collector) {
        if ('sql' != collector.type) return
        if (!collector.enabled) return
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
        binding.setProperty('LOG', LoggerFactory.getLogger("ROOT"))
        def icz = new ImportCustomizer()
        config.addCompilationCustomizers(icz)
        icz.addImports(JSON.class.name, JSONObject.class.name, Utils.class.name)
        Closure sqlScript = new GroovyShell(Thread.currentThread().contextClassLoader, binding, config).evaluate("{ -> $collector.sqlScript }")

        // 缓存时间计算函数
        Closure cacheTimeoutFn = buildCacheTimeoutFn(collector)
        collectorHolders.put(collector.id, new CollectorHolder(collector: collector, sql: db, dataKeyFn: { ctx -> //数据结果唯一性key计算逻辑
            computeCollectDataKey(collector, ctx)
        }, computeFn: { ctx -> //sql脚本执行函数
            Object result // 结果
            Exception exx //异常
            String dataKey //数据缓存key
            boolean cache = false //结果是否取自缓存
            Date start //取数据开始时间
            long spend
            def logMsg = "${ctx.logPrefix()}${ -> cache ? '(缓存)' : ''}Sql收集器'${collector.name}(${collector.id})'${ -> spend ? ', spend: ' + spend : ''}, result: ${ -> result}"

            //1. 先从缓存中取
            if (cacheTimeoutFn) {
                dataKey = computeCollectDataKey(collector, ctx)
                if (redis) {
                    start = new Date() // 调用时间
                    result = redis.get(getStr("collectorCacheKeyPrefix", "collector") + ":" + dataKey)
                    spend = System.currentTimeMillis() - start.time
                } else if (cacheSrv) {
                    start = new Date() // 调用时间
                    result = cacheSrv.get(getStr("collectorCacheKeyPrefix", "collector") +":"+ dataKey)?.toString()
                    spend = System.currentTimeMillis() - start.time
                }
            }
            //2. 执行sql脚本
            if (result == null || result.toString().isEmpty()) {
                try {
                    start = new Date() // 调用时间
                    result = sqlScript.rehydrate(ctx.data, sqlScript, this)()
                    spend = System.currentTimeMillis() - start.time
                    log.info(logMsg.toString())
                } catch (ex) {
                    exx = ex
                    log.error(logMsg.toString(), ex)
                }
            } else {
                cache = true
                log.info(logMsg.toString())
            }
            //3. 缓存结果
            if (result != null && !result.toString().isEmpty() && dataKey && !cache) { //缓存结果
                setCache(ctx, cacheTimeoutFn, dataKey, result)
            }
            //4. 保存结果
            dataCollected(collector, ctx, cache, start, spend, (exx ? '0001' : '0000'), result, exx, null, null)
            return result
        }, testComputeFn: {param -> // 测试:sql脚本执行函数
            def result = sqlScript.rehydrate(param, sqlScript, this)()
            if (result instanceof Map) {
                return result.collectEntries {e -> [e.key, e.value instanceof Closure ? e.value() : e.value]}
            }
            return result
        }))
    }


    /**
     * 初始化 script 收集器
     */
    protected void initScriptCollector(DataCollector collector) {
        if ('script' != collector.type) return
        if (!collector.enabled) return
        if (!collector.computeScript) {
            log.warn("Script collector'$collector.name' script must not be empty".toString()); return
        }
        Binding binding = new Binding()
        binding.setProperty('LOG', LoggerFactory.getLogger("ROOT"))
        def config = new CompilerConfiguration()
        def icz = new ImportCustomizer()
        config.addCompilationCustomizers(icz)
        icz.addImports(JSON.class.name, JSONObject.class.name, Utils.class.name)
        Closure script = new GroovyShell(Thread.currentThread().contextClassLoader, binding, config).evaluate("{ -> $collector.computeScript}")

        // 缓存时间计算函数
        Closure cacheTimeoutFn = buildCacheTimeoutFn(collector)
        collectorHolders.put(collector.id, new CollectorHolder(collector: collector, dataKeyFn: { ctx -> //数据结果唯一性key计算逻辑
            computeCollectDataKey(collector, ctx)
        }, computeFn: { ctx ->
            def result
            Date start
            long spend
            String dataKey // 缓存key
            boolean cache = false // 是否取自缓存
            Exception ex
            def logMsg = "${ctx.logPrefix()}${ -> cache ? '(缓存)' : ''}脚本收集器'${collector.name}(${collector.id})'${ -> ', spend: ' + spend}, result: ${ -> result}"

            //1. 先从缓存中取
            if (cacheTimeoutFn) {
                dataKey = computeCollectDataKey(collector, ctx)
                if (redis) {
                    start = new Date() // 调用时间
                    result = redis.get(getStr("collectorCacheKeyPrefix", "collector") + ":" +dataKey)
                    spend = System.currentTimeMillis() - start.time
                } else if (cacheSrv) {
                    start = new Date() // 调用时间
                    result = cacheSrv.get(getStr("collectorCacheKeyPrefix", "collector") + ":" +dataKey)?.toString()
                    spend = System.currentTimeMillis() - start.time
                }
            }
            //2. 调用脚本
            if (!result) {
                try {
                    start = new Date() // 调用时间
                    result = script.rehydrate(ctx.data, script, this)()
                    spend = System.currentTimeMillis() - start.time
                    log.info(logMsg.toString())
                } catch (e) {
                    ex = e
                    log.error(logMsg.toString(), ex)
                }
            } else {
                cache = true
                log.error(logMsg.toString(), ex)
            }
            //3. 缓存结果
            if (!cache && result != null && !result.toString().isEmpty() && dataKey) {
                setCache(ctx, cacheTimeoutFn, dataKey, result)
            }
            //4. 保存结果
            dataCollected(collector, ctx, cache, start, spend, (ex ? '0001' : '0000'), result, ex, null, null)
            return result
        }, testComputeFn: {param ->
            def result = script.rehydrate(param, script, this)()
            if (result instanceof Map) {
                return result.collectEntries {e -> [e.key, e.value instanceof Closure ? e.value() : e.value]}
            }
            return result
        }))
    }


    /**
     * 初始化 http 收集器
     */
    protected void initHttpCollector(DataCollector collector) {
        if ('http' != collector.type) return
        if (!collector.enabled) return
        // 创建 http 客户端
        def http = new OkHttpSrv('okHttp_' + collector.id); app().inject(http)
        http.setAttr('connectTimeout', getLong("http.connectTimeout." + collector.id, getLong('http.connectTimeout', 3L)))
        http.setAttr('readTimeout', getLong("http.readTimeout." + collector.id, getLong('http.readTimeout', Long.valueOf(collector.timeout?:20))))
        http.init()

        // 结果解析函数
        Closure parseFn
        if (collector.parseScript) {
            Binding binding = new Binding()
            binding.setProperty('LOG', LoggerFactory.getLogger("ROOT"))
            def config = new CompilerConfiguration()
            def icz = new ImportCustomizer()
            config.addCompilationCustomizers(icz)
            icz.addImports(JSON.class.name, JSONObject.class.name, Utils.class.name)
            parseFn = new GroovyShell(Thread.currentThread().contextClassLoader, binding, config).evaluate("$collector.parseScript")
        }
        // 缓存时间计算函数
        Closure cacheTimeoutFn = buildCacheTimeoutFn(collector)
        // 是否成功判断函数
        Closure successFn
        if (collector.dataSuccessScript) {
            Binding binding = new Binding()
            binding.setProperty('LOG', LoggerFactory.getLogger("ROOT"))
            def config = new CompilerConfiguration()
            def icz = new ImportCustomizer()
            config.addCompilationCustomizers(icz)
            icz.addImports(JSON.class.name, JSONObject.class.name, Utils.class.name)
            successFn = new GroovyShell(Thread.currentThread().contextClassLoader, binding, config).evaluate("$collector.dataSuccessScript")
        }

        // GString 模板替换
        def tplEngine = new GStringTemplateEngine(Thread.currentThread().contextClassLoader)
        collectorHolders.put(collector.id, new CollectorHolder(collector: collector, dataKeyFn: { ctx -> //数据结果唯一性key计算逻辑
            computeCollectDataKey(collector, ctx)
        }, computeFn: { ctx -> // 数据集成中3方接口访问过程
            String result // 接口返回结果字符串
            Object resolveResult // 解析接口返回结果
            long spend = 0 // 取数据,(网络)耗时时长
            Date start //取数据开始时间
            String url = collector.url // http请求 url
            String bodyStr = collector.bodyStr // http 请求 body字符串
            String dataKey // 数据缓存key
            boolean cache = false //结果是否取自缓存
            Integer respCode // http响应码

            String retryMsg = '' //重试消息
            // 日志字符串
            def logMsg = "${ctx.logPrefix()}${ -> cache ? '(缓存)' : ''}接口收集器'$collector.name(${collector.id})'${ -> retryMsg}, url: ${ -> url}${ -> bodyStr == null ? '' : ', body: ' + bodyStr}${ -> spend ? ', spend: ' + spend : ''}${ -> respCode ? ', respCode: ' + respCode : ''}${ -> ', result: ' + result}${ -> resolveResult == null ? '' : ', resolveResult: ' + (resolveResult instanceof Map ? resolveResult.findAll {e -> !(e.value instanceof Closure)} : (resolveResult instanceof Closure ? '' : resolveResult))}"

            //1. 先从缓存中取
            if (cacheTimeoutFn) {
                dataKey = computeCollectDataKey(collector, ctx)
                if (redis) {
                    start = new Date() // 调用时间
                    result = redis.get(getStr("collectorCacheKeyPrefix", "collector") + ":" +dataKey)
                    spend = System.currentTimeMillis() - start.time
                } else if (cacheSrv) {
                    start = new Date() // 调用时间
                    result = cacheSrv.get(getStr("collectorCacheKeyPrefix", "collector") + ":" +dataKey)?.toString()
                    spend = System.currentTimeMillis() - start.time
                }
            }

            //2. 未从缓存中取到结果, 则调接口, 网络连接错误,主动重试3次
            if (!result) {
                // url 替换 ${} 变量
                for (int i = 0; i < 2; i++) {
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
                            log.error("$collector.name url config error, not allow set property '$key'".toString())
                            null
                        }
                    }).toString()
                }

                // body 替换 ${} 变量
                for (int i = 0; i < 2; i++) {
                    if (!bodyStr || !bodyStr.contains('${')) break
                    bodyStr = tplEngine.createTemplate(bodyStr).make(new HashMap(1) {
                        @Override
                        boolean containsKey(Object key) { return true } // 加这行是为了 防止 MissingPropertyException
                        @Override
                        Object get(Object key) { ctx.data.get(key)?:"" }
                        @Override
                        Object put(Object key, Object value) {
                            log.error("$collector.name bodyStr config error, not allow set property '$key'".toString())
                            null
                        }
                    }).toString()
                }
                try {
                    start = new Date() // 调用时间
                    for (int i = 0, times = getInteger('http.retry', 2) + 1; i < times; i++) { // 接口一般遇网络错重试2次
                        try {
                            retryMsg = i > 0 ? "(重试第${i}次)" : ''
                            if ('get'.equalsIgnoreCase(collector.method)) {
                                def h = http.get(url)
                                result = h.execute()
                                respCode = h.respCode
                            } else if ('post'.equalsIgnoreCase(collector.method)) {
                                def h = http.post(url)
                                result = h.textBody(bodyStr).contentType(collector.contentType).execute()
                                respCode = h.respCode
                            } else throw new Exception("Not support http method $collector.method")
                            break
                        } catch (ex) {
                            if ((ex instanceof ConnectException) && (i + 1) < times) {
                                log.warn(logMsg.toString() + ", 异常: " + (ex.class.simpleName + ': ' + ex.message))
                                continue
                            } else throw ex
                        } finally {
                            spend = System.currentTimeMillis() - start.time
                        }
                    }
                } catch (ex) {
                    log.error(logMsg.toString(), ex)
                    dataCollected(collector, ctx, cache, start, spend, '0001', result, ex, null, null, url, bodyStr)
                    return null
                } finally {
                    retryMsg = ''
                }
            } else {
                cache = true
            }

            //3. 判断http返回结果是否为有效数据. 默认有效(0000)
            String dataStatus = successFn ? (successFn.rehydrate(ctx.data, successFn, this)(result, respCode) ? '0000' : '0001') : '0000'

            //4. 如果接口返回的是有效数据, 则缓存
            if ('0000' == dataStatus && dataKey && !cache) setCache(ctx, cacheTimeoutFn, dataKey, result)

            //5. 解析接口返回结果
            if (parseFn && dataStatus == '0000') {
                Exception ex
                try {
                    resolveResult = parseFn.rehydrate(ctx.data, parseFn, this)(result, respCode)
                } catch (e) {
                    ex = e
                } finally {
                    if (ex) log.error(logMsg.toString() + ", 解析函数执行失败", ex)
                    else log.info(logMsg.toString())
                    dataCollected(collector, ctx, cache, start, spend, dataStatus, result, null, resolveResult, ex, url, bodyStr)
                }
                return resolveResult
            }

            log.info(logMsg.toString())
            dataCollected(collector, ctx, cache, start, spend, dataStatus, result, null, resolveResult, null, url, bodyStr)
            return dataStatus == '0000' ? result : null
        }, testComputeFn: {param ->
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
                        def v = param.get(key)
                        // url前缀不必编码, 其它参数需要编码
                        return v == null ? '' : (paramIndex==1 && v.toString().startsWith("http") && collector.url.startsWith('${') ? v : URLEncoder.encode(v.toString(), 'utf-8'))
                    }

                    @Override
                    Object put(Object key, Object value) {
                        log.error("$collector.name url config error, not allow set property '$key'".toString())
                        null
                    }
                }).toString()
            }

            // http 请求 body字符串
            String bodyStr = collector.bodyStr
            for (int i = 0; i < 2; i++) { // 替换 ${} 变量
                if (!bodyStr || !bodyStr.contains('${')) break
                bodyStr = tplEngine.createTemplate(bodyStr).make(new HashMap(1) {
                    @Override
                    boolean containsKey(Object key) { return true } // 加这行是为了 防止 MissingPropertyException
                    @Override
                    Object get(Object key) { param.get(key)?:"" }
                    @Override
                    Object put(Object key, Object value) {
                        log.error("$collector.name bodyStr config error, not allow set property '$key'".toString())
                        null
                    }
                }).toString()
            }
            // NOTE: 如果是json 并且是,} 结尾, 则删除 最后的,(因为spring解析入参数会认为json格式错误)
            // if (bodyStr.endsWith(',}')) bodyStr = bodyStr.substring(0, bodyStr.length() - 3) + '}'

            String result // 接口返回结果字符串
            def resolveResult // 解析接口返回结果

            Integer respCode //http响应码
            if ('get'.equalsIgnoreCase(collector.method)) {
                def h = http.get(url)
                result = h.debug().execute()
                respCode = h.respCode
            } else if ('post'.equalsIgnoreCase(collector.method)) {
                def h = http.post(url)
                result = h.textBody(bodyStr).contentType(collector.contentType).debug().execute()
                respCode = h.respCode
            } else throw new Exception("Not support http method $collector.method")

            // http 返回结果成功判断. 默认成功
            String dataStatus = successFn ? (successFn.rehydrate(param, successFn, this)(result, respCode) ? '0000' : '0001') : '0000'

            if (parseFn && dataStatus == '0000') { // 解析接口返回结果
                resolveResult = parseFn.rehydrate(param, parseFn, this)(result, respCode)
                if (resolveResult instanceof Map) {
                    return resolveResult.collectEntries {e -> [e.key, e.value instanceof Closure ? e.value() : e.value]}
                }
                return resolveResult
            }
            return dataStatus == '0000' ? result : null
        }))
    }


    /**
     * 计算收集器的数据缓存key
     * @param collector 收集器
     * @param ctx 当前执行上下文
     * @return dataKey
     */
    protected String computeCollectDataKey(DataCollector collector, DecisionContext ctx) {
        if (!collector.cacheKey) return collector.id //未配置,dataKey为收集器id
        String dataKey = collector.cacheKey
        if (dataKey.contains('${')) {
            def tplEngine = new GStringTemplateEngine(Thread.currentThread().contextClassLoader)
            for (int i = 0; i < 2; i++) { // 替换 ${} 变量
                if (!dataKey.contains('${')) break
                dataKey = tplEngine.createTemplate(dataKey).make(new HashMap(1) {
                    int paramIndex
                    @Override
                    boolean containsKey(Object key) { return true } // 加这行是为了 防止 MissingPropertyException
                    @Override
                    Object get(Object key) { return ctx.data.get(key) }

                    @Override
                    Object put(Object key, Object value) {
                        log.error("$collector.name cacheKey config error, not allow set property '$key'".toString())
                        null
                    }
                }).toString()
            }
        }

        return collector.id + '_' + dataKey
    }


    /**
     * 缓存结果
     * @param ctx 当前执行上下文
     * @param timeoutFn 缓存时间计算函数
     * @param dataKey 缓存数据key
     * @param result 要缓存的数据
     */
    protected void setCache(DecisionContext ctx, Closure timeoutFn, String dataKey, def result) {
        def cacheTimeout = timeoutFn.rehydrate(ctx.data, timeoutFn, this)()
        if (cacheTimeout instanceof Date) {
            long v = cacheTimeout.time - System.currentTimeMillis()
            if (v < 2000) {
                log.warn(ctx.logPrefix() + "缓存过期时间不能小于当前时间. dataKey: " + dataKey)
                return
            }
            cacheTimeout = Duration.ofMillis(v)
        } else if (cacheTimeout instanceof Integer) {
            if (cacheTimeout <= 0) return
            cacheTimeout = Duration.ofMinutes(cacheTimeout)
        } else if (cacheTimeout instanceof Duration) {

        } else throw new RuntimeException("缓存过期时间函数返回类型错误")
        if (cacheTimeout.seconds <= 0) return
        String key = getStr("collectorCacheKeyPrefix", "collector") +":"+ dataKey
        if (redis) {
            redis.set(key, result.toString())
            redis.expire(key, cacheTimeout.seconds)
        }
        else if (cacheSrv) {
            cacheSrv.set(key, result, cacheTimeout)
        }
    }


    /**
     * 构建收集器 缓存时间计算函数
     * @param collector 收集器
     */
    protected Closure buildCacheTimeoutFn(DataCollector collector) {
        if (collector.cacheTimeoutFn) {
            Binding binding = new Binding()
            def config = new CompilerConfiguration()
            def icz = new ImportCustomizer()
            config.addCompilationCustomizers(icz)
            icz.addImports(JSON.class.name, JSONObject.class.name, Utils.class.name)
            return new GroovyShell(Thread.currentThread().contextClassLoader, binding, config).evaluate("{ -> $collector.cacheTimeoutFn }")
        }
        null
    }


    /**
     * 收集器 Holder
     */
    class CollectorHolder {
        // 对应数据库中的实体
        DataCollector collector
        // 把收集器转换的执行函数
        Function<DecisionContext, Object> computeFn
        // 数据收集上下文唯一key: 用于标识当前执行上下文是否需要重复计算当前收集器的结果
        Function<DecisionContext, String> dataKeyFn
        // 单元测试函数
        Function<Map, Object> testComputeFn
        // DB
        Sql sql

        void close() {
            try {
                sql?.dataSource?.invokeMethod('close', null)
                sql?.close()
            } catch (ex) {}
        }


        /**
         * 属性值收集
         * @param aName 属性名
         * @param ctx 执行上下文
         * @return 属性值
         */
        def populate(String aName, DecisionContext ctx) {
            String dataKey = dataKeyFn.apply(ctx) //数据key,判断是否需要重新执行收集器的计算函数拿结果
            // 值get函数
            def valueGetFn = { ->
                def collectResult = ctx.dataCollectResult.get(dataKey)
                if (collectResult instanceof Map) { //收集器有多个属性的值
                    def value
                    String key
                    if (collectResult.containsKey(aName)) {
                        key = aName
                        value = collectResult.get(key)
                    } else {
                        key = fieldManager.alias(aName)
                        value = collectResult.get(key)
                    }
                    if (value instanceof Closure) { // 延迟计算函数,只计算一次
                        value = value()
                        collectResult.put(key, value)
                    }
                    return value
                } else if (collectResult instanceof Closure) {
                    log.error(ctx.logPrefix() + "收集器'${collector.name}(${collector.id})'结果不能是个函数")
                }
                return collectResult
            }
            // 收集器已经执行过
            if (ctx.dataCollectResult.containsKey(dataKey)) return valueGetFn()

            ctx.dataCollectResult.put(dataKey, null) //占位,避免循环计算
            // 函数执行
            log.debug(ctx.logPrefix() + "Get '${aName}' value apply function: '${collector.name}(${collector.id})'".toString())
            def collectResult = null
            try {
                collectResult = computeFn.apply(ctx)
            } catch (ex) { // 接口执行报错, 默认继续往下执行规则
                log.error(ctx.logPrefix() + "数据收集器'${collector.name}(${collector.id})' 执行错误".toString(), ex)
            }
            ctx.dataCollectResult.put(dataKey, collectResult)
            return valueGetFn()
        }
    }
}
package service.rule

import cn.xnatural.enet.event.EL
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import core.OkHttpSrv
import core.ServerTpl
import core.Utils
import core.jpa.BaseRepo
import dao.entity.DataCollector
import dao.entity.FieldType
import dao.entity.RuleField
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

    @Lazy def                                                      repo             = bean(BaseRepo, 'jpa_rule')
    /**
     * RuleField(enName, cnName), RuleField
     */
    final Map<String, RuleField>                                   attrMap          = new ConcurrentHashMap<>(1000)
    /**
     * 数据获取函数. 函数名 -> 函数
     */
    protected final Map<String, Function<DecisionContext, Object>> dataCollectorMap = new ConcurrentHashMap()
    @Lazy Set<String>                                              ignoreGetAttr    = new HashSet<>(
        (getStr('ignoreGetAttr', '')).split(",").collect {it.trim()}.findAll{it}
    )


    @EL(name = 'jpa_rule.started', async = true)
    void init() {
        // 有顺序
        loadAttrs()
        loadDataCollector()
        ep.fire("${name}.started")
    }


    /**
     * 获取属性值
     * @param aName 属性名
     * @param ctx
     * @return
     */
    def getAttr(String aName, DecisionContext ctx) {
        if (ignoreGetAttr.contains(aName) || ignoreGetAttr.contains(alias(aName))) return null
        def fnName = attrMap.get(aName)?.dataCollector
        if (fnName == null) {
            log.warn(ctx.logPrefix() + "属性'" + aName + "'没有对应的取值函数")
            return null
        }

        // 函数执行
        def doApply = {Function<DecisionContext, Object> fn ->
            log.debug(ctx.logPrefix() + "Get attr '{}' value apply function: '{}'", aName, fnName)
            fn.apply(ctx)
        }

        def fn = dataCollectorMap.get(fnName)
        if (fn) {
            return doApply(fn)
        } else {
            dataCollect( // 重新去数据库中查找
                repo.find(DataCollector) {root, query, cb -> cb.equal(root.get('enName'), fnName)}
            )
            fn = dataCollectorMap.get(fnName)
            if (fn) return doApply(fn)
            else {
                log.debug(ctx.logPrefix() + "Not fund attr '{}' mapped getter function '{}'", aName, fnName)
                return null
            }
        }
    }


    /**
     * 决策产生的数据接口调用
     * @param ctx 当前决策 DecisionContext
     * @param dataCfg 数据配置
     * @param jsonBody 入参json
     * @param resultStr 接口返回结果
     * @param resolveResult 解析结果
     * @param spend 接口调用花费时间
     */
    @EL(name = 'decision.dataCollect')
    void dataCollected(DecisionContext ctx, Map dataCfg, String jsonBody, String resultStr, Map resolveResult, Long spend) {
        log.info(ctx.logPrefix() + "接口调用: " + dataCfg['name'] + ", spend: " + spend + "ms, params: " + jsonBody + ", returnStr: " + resultStr + ", resolveResult: " + resolveResult)
    }


    /**
     * 数据获取函数
     * @param fnName 函数名
     * @param fn 函数
     */
    Function<DecisionContext, Object> dataFn(String fnName, Function<DecisionContext, Object> fn) { dataCollectorMap.put(fnName, fn) }


    /**
     * 得到属性对应的别名
     * @param aName 属性名
     * @return null: 没有别名
     */
    String alias(String aName) {
        def record = attrMap.get(aName)
        if (record.cnName == aName) return record.enName
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
        Utils.to(aValue, attrMap.get(aName).type.clzType)
    }


    /**
     * 加载属性
     */
    void loadAttrs() {
        initAttr()
        log.info("加载属性配置")
        repo.findList(RuleField).each {field ->
            attrMap.put(field.enName, field)
            attrMap.put(field.cnName, field)
        }
    }


    /**
     * 初始化默认属性集
     */
    protected void initAttr() {
        if (repo.count(RuleField) == 0) {
            log.info("初始化默认属性集")
            repo.saveOrUpdate(new RuleField(enName: 'idNumber', cnName: '身份证', type: FieldType.Str))
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
        initDataCollector()
        log.info("加载数据集成配置")
        repo.findList(DataCollector).each {dataCollect(it)}
    }


    /**
     * 初始化默认数据集
     */
    protected void initDataCollector() {
        if (repo.count(DataCollector) == 0) {
            log.info("初始化默认数据集")
            repo.saveOrUpdate(new DataCollector(type: 'script', enName: 'week', cnName: '星期几', comment: '值: 1,2,3,4,5,6,7', computeScript: """
                Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
            """))
            repo.saveOrUpdate(new DataCollector(type: 'script', enName: 'gender', cnName: '性别', comment: '根据身份证计算. 值: F,M', computeScript: """
                if (idNumber && idNumber.length() >= 17) {
                    Integer.parseInt(idNumber.substring(16, 17)) % 2 == 0 ? 'F' : 'M'
                }
                null
            """))
            repo.saveOrUpdate(new DataCollector(type: 'script', enName: 'age', cnName: '年龄', comment: '根据身份证计算', computeScript: """
                if (idNumber && idNumber.length() >= 17) {
                    Calendar cal = Calendar.getInstance()
                    int yearNow = cal.get(Calendar.YEAR)
                    int monthNow = cal.get(Calendar.MONTH)+1
                    int dayNow = cal.get(Calendar.DATE)
            
                    int birthday = Integer.valueOf(idNumber.substring(6, 14))
                    int year = Integer.valueOf(idNumber.substring(10, 12))
                    int day = Integer.valueOf(idNumber.substring(12, 14))
                    
                    if ((month < monthNow) || (month == monthNow && day <= dayNow)){
                        yearNow - year
                    } else {
                        yearNow - year - 1
                    }
                }
                null
            """))
        }
    }


    /**
     * 初始全数据收集器
     * @param record
     */
    void dataCollect(DataCollector record) {
        if (!record) return
        if ('interface' == record.type) {
            def http = bean(OkHttpSrv)
            Closure parseFn
            if (record.parseScript) {
                Binding binding = new Binding()
                def config = new CompilerConfiguration()
                def icz = new ImportCustomizer()
                config.addCompilationCustomizers(icz)
                icz.addImports(JSON.class.name, JSONObject.class.name)
                parseFn = new GroovyShell(Thread.currentThread().contextClassLoader, binding, config).evaluate(record.parseScript)
            }
            dataFn(record.enName) {ctx ->
                String result
                def urlFn = { record.url }
                urlFn.delegate = ctx.data; urlFn.resolveStrategy = Closure.DELEGATE_ONLY
                if ('get'.equalsIgnoreCase(record.method)) {
                    result = http.get(urlFn()).execute()
                } else if ('post'.equalsIgnoreCase(record.method)) {
                    def bodyFn = { record.bodyStr }
                    bodyFn.delegate = ctx.data; bodyFn.resolveStrategy = Closure.DELEGATE_ONLY
                    result = http.post(urlFn()).textBody(bodyFn()).contentType(record.contentType).execute()
                } else throw new Exception("Not support http method $record.method")
                if (parseFn) {
                    def fn = parseFn.rehydrate(ctx.data, parseFn, this)
                    fn.resolveStrategy = Closure.DELEGATE_FIRST
                    return fn(result)
                }
                return result
            }
        } else if ('script' == record.type) {
            if (record.computeScript) {
                Binding binding = new Binding()
                def config = new CompilerConfiguration()
                def icz = new ImportCustomizer()
                config.addCompilationCustomizers(icz)
                icz.addImports(JSON.class.name, JSONObject.class.name)
                Closure script = new GroovyShell(Thread.currentThread().contextClassLoader, binding, config).evaluate(record.computeScript)
                dataFn(record.enName) { ctx ->
                    def fn = script.rehydrate(ctx.data, script, this)
                    fn.resolveStrategy = Closure.DELEGATE_FIRST
                    return fn()
                }
            }
        } else throw new Exception("Not support type: $record.type")
    }
}

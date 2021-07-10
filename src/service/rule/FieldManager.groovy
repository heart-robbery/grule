package service.rule


import cn.xnatural.app.ServerTpl
import cn.xnatural.app.Utils
import cn.xnatural.enet.event.EC
import cn.xnatural.enet.event.EL
import cn.xnatural.jpa.Repo
import cn.xnatural.remoter.Remoter
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import entity.DataCollector
import entity.FieldType
import entity.RuleField
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.slf4j.LoggerFactory

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 属性管理
 * 属性别名
 * 属性值函数
 */
class FieldManager extends ServerTpl {
    @Lazy def repo = bean(Repo, 'jpa_rule_repo')
    @Lazy def collectorManager = bean(CollectorManager)
    /**
     * RuleField(enName, cnName), RuleField
     */
    final Map<String, FieldHolder> fieldHolders = new ConcurrentHashMap<>(500)



    /**
     * 执行数据收集, 获取属性值
     * @param aName 属性名
     * @param ctx 决策执行上下文
     * @return 当前属性的值
     */
    def dataCollect(String aName, DecisionContext ctx) {
        def fieldHolder = fieldHolders.get(aName)
        if (fieldHolder == null) {
            log.warn(ctx.logPrefix() + "未找到属性'$aName'对应的配置".toString())
            return null
        }
        String collectorId = fieldHolder.choose(ctx) // 属性对应的 值 收集器名
        if (!collectorId) {
            log.warn(ctx.logPrefix() + "属性'" + aName + "'没有对应的取值配置")
            return null
        }
        collectorManager.populate(collectorId, aName, ctx)
    }


    /**
     * 得到属性对应的别名
     * @param aName 属性名
     * @return null: 没有别名
     */
    String alias(String aName) {
        def field = fieldHolders.get(aName)?.field
        if (field == null) return null
        else if (field.cnName == aName) return field.enName
        else if (field.enName == aName) return field.cnName
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
        def field = fieldHolders.get(aName)?.field
        if (field == null) return aValue
        Utils.to(aValue, field.type.clzType)
    }


    // ======================= 监听变化 ==========================
    @EL(name = ['fieldChange', 'field.dataVersion'], async = true)
    void listenFieldChange(EC ec, Long id) {
        def field = repo.findById(RuleField, id)
        if (field == null) {
            for (def itt = fieldHolders.iterator(); itt.hasNext(); ) {
                def e = itt.next()
                if (e.value.field.id == id) itt.remove()
            }
            log.info("delField: " + id)
        } else {
            initField(field)
            log.info("fieldChanged: ${field.cnName}, $id".toString())
        }
        def remoter = bean(Remoter)
        if (remoter && ec?.source() != remoter) { // 不是远程触发的事件
            remoter.dataVersion('field').update(id.toString(), field ? field.updateTime.time : System.currentTimeMillis(), null)
        }
    }


    /**
     * 加载所有指标
     */
    @EL(name = 'jpa_rule.started', async = true, order = 1f)
    void load() {
        initDefaultField()
        final Set<String> names = (fieldHolders ? ConcurrentHashMap.newKeySet(fieldHolders.size() / 2) : null)
        final def threshold = new AtomicInteger(1)
        final def tryComplete = {
            if (threshold.decrementAndGet() > 0) return
            if (names) { // 重新加载, 要删除内存中有, 但库中没有
                fieldHolders.findAll {!names.contains(it.key)}.each { e ->
                    fieldHolders.remove(e.key)
                }
            }
            log.info("加载字段属性 {}个", fieldHolders.size() / 2)
        }
        for (int page = 0, limit = 50; ; page++) {
            def ls = repo.findList(RuleField, page * limit, limit)
            threshold.incrementAndGet()
            async {
                ls.each {field ->
                    initField(field)
                    names?.add(field.enName)
                    names?.add(field.cnName)
                }
                tryComplete()
            }
            if (!ls || ls.size() < limit) {
                tryComplete(); break
            }
        }
    }


    /**
     * 初始化默认属性集
     */
    protected void initDefaultField() {
        if (repo.count(RuleField) == 0) {
            log.info("初始化默认属性集")
            repo.saveOrUpdate(new RuleField(enName: 'idNumber', cnName: '身份证号码', type: FieldType.Str, decision: ''))
            repo.saveOrUpdate(new RuleField(enName: 'name', cnName: '姓名', type: FieldType.Str, decision: ''))
            repo.saveOrUpdate(new RuleField(enName: 'mobileNo', cnName: '手机号码', type: FieldType.Str, decision: ''))
            repo.saveOrUpdate(new RuleField(enName: 'age', cnName: '年龄', type: FieldType.Int, decision: '', comment: '根据身份整计算',
                collectorOptions: JSON.toJSONString([[
                        collectorId: repo.find(DataCollector) {root, query, cb -> cb.equal(root.get("name"), "年龄")}?.id,
                        chooseFn: 'true'
                ]])))
            repo.saveOrUpdate(new RuleField(enName: 'gender', cnName: '性别', type: FieldType.Str, decision: '', comment: '值: F(女),M(男)',
                collectorOptions: JSON.toJSONString([[
                        collectorId: repo.find(DataCollector) {root, query, cb -> cb.equal(root.get("name"), "性别")}?.id,
                        chooseFn: 'true'
                ]])))
            repo.saveOrUpdate(new RuleField(enName: 'week', cnName: '星期几', type: FieldType.Int, decision: '', comment: '值: 1,2,3,4,5,6,7',
                collectorOptions: JSON.toJSONString([[
                        collectorId: repo.find(DataCollector) {root, query, cb -> cb.equal(root.get("name"), "星期几")}?.id,
                        chooseFn: 'true'
                ]])))
            repo.saveOrUpdate(new RuleField(enName: 'currentDateTime', cnName: '当前日期时间', type: FieldType.Str, decision: '', comment: '值: yyyy-MM-dd HH:mm:ss',
                collectorOptions: JSON.toJSONString([[
                        collectorId: repo.find(DataCollector) {root, query, cb -> cb.equal(root.get("name"), "当前日期时间")}?.id,
                        chooseFn: 'true'
                ]])))
            repo.saveOrUpdate(new RuleField(enName: 'currentDate', cnName: '当前日期', type: FieldType.Str, decision: '', comment: '值: yyyy-MM-dd',
                collectorOptions: JSON.toJSONString([[
                        collectorId: repo.find(DataCollector) {root, query, cb -> cb.equal(root.get("name"), "当前日期")}?.id,
                        chooseFn: 'true'
                ]])))
            repo.saveOrUpdate(new RuleField(enName: 'currentTime', cnName: '当前时间', type: FieldType.Str, decision: '', comment: '值: HH:mm:ss',
                collectorOptions: JSON.toJSONString([[
                        collectorId: repo.find(DataCollector) {root, query, cb -> cb.equal(root.get("name"), "当前时间")}?.id,
                        chooseFn: 'true'
                ]])))
        }
    }


    /**
     * 初始化 {@link RuleField}
     */
    protected void initField(RuleField field) {
        Binding binding = new Binding()
        def config = new CompilerConfiguration()
        binding.setProperty('LOG', LoggerFactory.getLogger("ROOT"))
        def icz = new ImportCustomizer()
        config.addCompilationCustomizers(icz)
        icz.addImports(JSON.class.name, JSONObject.class.name, Utils.class.name)

        def holder = new FieldHolder(field: field, collectorChooseMap: field.collectorOptions ? JSON.parseArray(field.collectorOptions).collectEntries {JSONObject jo ->
            [jo['collectorId'], new GroovyShell(Thread.currentThread().contextClassLoader, binding, config).evaluate("{ -> $jo.chooseFn }")]
        } : null)
        fieldHolders.put(field.enName, holder)
        fieldHolders.put(field.cnName, holder)
    }


    /**
     * 字段/属性/指标 Holder
     */
    class FieldHolder {
        RuleField field
        LinkedHashMap<String, Closure> collectorChooseMap

        /**
         * 根据当前执行上下文选择出 属性的收集器
         * @param ctx 当前执行上下文
         * @return 收集器id
         */
        String choose(DecisionContext ctx) {
            if (collectorChooseMap == null) return null
            collectorChooseMap.find {e ->
                e.value.rehydrate(ctx.data, e.value, this)()
            }?.key
        }
    }
}
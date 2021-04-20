package service.rule

import cn.xnatural.enet.event.EP
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.atomic.AtomicBoolean

/**
 * 决策执行上下文
 */
class DecisionContext {
    protected static final Logger log = LoggerFactory.getLogger(DecisionContext)
    // 决策执行标识(id)
    final String id
    // 开始时间
    final Date startup = new Date()
    // 当前正在执行的策略
    final DecisionHolder decisionHolder
    // 字段管理器
    final FieldManager fieldManager
    // 事件中心: 用于通知策略执行过程中的产生想事件
    final EP ep
    // 输入参数
    final Map<String, Object> input

    // 执行过程中的产生的所有数据
    @Lazy Map<String, Object> data = new Data(this)
    // 执行的策略集
    final List<PassedPolicy> policies = new LinkedList<>()
    // 数据收集器名 -> 数据收集结果集. 不用 ConcurrentHashMap 因为不能放null值
    final Map<String, Object> dataCollectResult = new LinkedHashMap<>()
    // 过程数据: 决策(非策略,规则)
    final Map<String, Object> decisionData = new LinkedHashMap<>()

    // 是否已启动
    protected final def started = new AtomicBoolean(false)
    // 是否执行结束
    protected final def end = new AtomicBoolean(false)
    // 规则执行迭代器
    @Lazy protected def ruleIterator = new RuleIterator(this)
    // 当前正在执行的规则
    protected PassedRule curPassedRule
    // 当前正在执行的策略
    protected PassedPolicy curPassedPolicy

    // 执行结果 异常
    protected Throwable exception
    // 执行状态
    protected String status = '0001'
    // 最终决策结果
    DecideResult decideResult


    DecisionContext(String id, DecisionHolder decisionHolder, Map<String, Object> input, FieldManager fieldManager, EP ep) {
        this.id = id?:UUID.randomUUID().toString().replace('-', '')
        this.decisionHolder = decisionHolder
        this.input = input
        this.fieldManager = fieldManager
        this.ep = ep
    }


    /**
     * 开始执行流程
     */
    void start() {
        if (!started.compareAndSet(false, true)) return
        log.info(logPrefix() + "开始")
        decisionHolder.spec.operateFn?.call(data) // 预执行操作
        if (!ruleIterator.hasNext()) {
            decideResult = DecideResult.Accept
            exception = new Exception("没有可执行的策略")
            log.warn(logPrefix() + exception)
        }
        trigger()
    }


    /**
     * 触发执行流程: 遍历策略, 遍历规则
     */
    protected final void trigger() {
        if (end.get()) return
        try {
            while (ruleIterator.hasNext()) { // 依次执行规则
                def ruleSpec = ruleIterator.next()
                if (!ruleSpec) break
                if (!ruleSpec.enabled) continue
                def result = decide(ruleSpec) // 规则执行
                decideResult = result?:decideResult
                if (decideResult?.block) break
            }
            if (decideResult?.block || !ruleIterator.hasNext()) {
                end.set(true); decideResult = decideResult?:DecideResult.Accept; status = '0000'
                log.info(logPrefix() + "结束成功. 共执行: " + (summary()['spend']) + "ms "  + result())
                ep?.fire("decision.end", this)
            }
        } catch (Throwable ex) {
            end.set(true); decideResult = DecideResult.Reject; status = 'EEEE'; this.exception = ex
            log.error(logPrefix() + "结束错误. 共执行: " + (summary()['spend']) + "ms " + result(), ex)
            ep?.fire("decision.end", this)
        }
    }


    /**
     * 执行规则
     * @param RuleSpec 规则描述
     * @return DecideResult 决策结果
     */
    protected DecideResult decide(RuleSpec spec) {
        if (!spec.enabled) return null
        curPassedRule = new PassedRule(spec: spec, attrs: [规则名: spec.规则名, *:spec.attrs])
        curPassedPolicy.rules.add(curPassedRule)
        log.trace(logPrefix() + "开始执行规则")

        DecideResult result
        try {
            spec.fns.forEach(e -> {
                def r = e.v2.call(this.data)
                if ("Reject" == e.v1) {
                    result = r
                    log.trace(logPrefix() + "拒绝函数执行结果: " + r)
                } else if ("Accept" == e.v1) {
                    result = r
                    log.trace(logPrefix() + "通过函数执行结果: " + r)
                } else if ("Review" == e.v1) {
                    result = r
                    log.trace(logPrefix() + "人工审核函数执行结果: " + r)
                } else if ("Operate" == e.v1) {
                    log.trace(logPrefix() + "操作函数执行完成")
                } else if ("Clear" == e.v1) {
                    log.trace(logPrefix() + "清除函数执行完成")
                } else {
                    log.trace(logPrefix() + e.v1 + "函数执行完成. " + r)
                }
            })
            log.debug(logPrefix() + "结束执行规则. decision: " + result  + ", attrs: " + curPassedRule.data)
        } catch (ex) {
            result = DecideResult.Reject
            log.error(logPrefix() + "规则执行错误. " + result + ", attrs: " + curPassedRule.data)
            throw ex
        } finally {
            curPassedRule.result = result; curPassedPolicy.result = result; curPassedRule = null
        }
        return result
    }


    /**
     * 设置属性
     * @param aName 属性名
     * @param value 属性值
     */
    void setAttr(String aName, Object value) { data.put(aName, value) }


    /**
     * 过程数据记录: 规则中, 策略中, 决策中
     * 当前如果是正在执行规则,则记录到规则数据
     * 当前如果是正在执行策略,则记录到策略数据
     * 当前如果是正在执行决策,则记录到决策数据
     * @param key 属性key
     * @param value 属性值
     */
    protected void recordData(String key, Object value) {
        if (end.get()) return
        if (curPassedRule) curPassedRule.data.put(key, value)
        else if (curPassedPolicy) curPassedPolicy.data.put(key, value)
        else decisionData.put(key, value)
    }


    /**
     * 规则执行迭代器
     */
    protected class RuleIterator implements Iterator<RuleSpec> {
        final DecisionContext ctx

        RuleIterator(DecisionContext ctx) {this.ctx = ctx}

        // 策略迭代器
        Iterator<PolicySpec> policyItt = ctx.getDecisionHolder().spec.policies.iterator()
        // 规则迭代器
        Iterator<RuleSpec> ruleItt

        @Override
        boolean hasNext() { (ruleItt && ruleItt.hasNext()) || policyItt.hasNext() }

        @Override
        RuleSpec next() {
            // 执行下一个策略: 1. 没有正在执行策略; 2. 没有可执行的规则迭代器; 3. 规则迭代器没有可执行的规则
            if ((ctx.curPassedPolicy == null || (ruleItt == null || !ruleItt.hasNext())) && policyItt.hasNext()) {
                def spec = policyItt.next()
                ctx.curPassedPolicy = new PassedPolicy(spec: spec, attrs: [策略名: spec.策略名, *:spec.attrs])
                ctx.policies.add(ctx.curPassedPolicy)
                log.debug(logPrefix() + "开始执行策略")
                spec.fns.each {e ->
                    if (e.v1 == 'Condition') { // 执行策略条件函数. 条件函数返回false, 则跳出, 继续执行下一个策略
                        log.trace(logPrefix() + "开始执行策略条件函数")
                        if (!e.v2.call(ctx.data)) {
                            ctx.curPassedPolicy = null
                            return next()
                        }
                    } else if (e.v1 == 'Operate') { // 执行策略操作函数
                        log.trace(logPrefix() + "开始执行策略操作函数")
                        e.v2.call(ctx.data)
                    } else throw new IllegalArgumentException('策略不支持类型函数: ' + e.v1)
                }
                // 创建策略的规则迭代器
                ruleItt = spec.rules.iterator()
            }

            if (ruleItt && ruleItt.hasNext()) return ruleItt.next()
            return null
        }
    }


    /**
     * 数据存放. 用于规则闭包执行上下文
     */
    protected class Data extends LinkedHashMap<String, Object> {
        final DecisionContext ctx

        Data(DecisionContext ctx) {this.ctx = ctx}


        /**
         * 查找已存在的属性
         * @param aName 属性名
         * @return 属性值
         */
        Object find(Object aName) { super.get(aName) }


        @Override
        Object get(Object aName) {
            if (aName == null) return null
            //函数名
            if (ctx.decisionHolder.spec.functions.containsKey(aName)) return null

            def value = super.get(aName)
            if (value == null && !super.containsKey(aName) && !ctx.end.get()) {// 属性值未找到,则从属性管理器获取
                // 代表属性已从外部获取过,后面就不再去获取了(防止重复获取). TODO 循环属性获取链
                safeSet(aName.toString(), null)
                boolean fromInput
                if (ctx.input) { //先从入参里面获取
                    if (ctx.input.containsKey(aName)) {
                        fromInput = true
                        safeSet((String) aName, ctx.input.get(aName))
                    } else {
                        def alias = ctx.getFieldManager().alias(aName)
                        if (alias != null && ctx.input.containsKey(alias)) {
                            fromInput = true
                            safeSet((String) aName, ctx.input.get(alias))
                        }
                    }
                }
                if (!fromInput) { //再从收集器获取
                    safeSet((String) aName, ctx.getFieldManager().dataCollect(aName.toString(), ctx))
                }
                value = super.get(aName)
            }
            if (value instanceof Optional) {
                if (value.present) value = value.get()
                else value = null
            }

            ctx.recordData(aName.toString(), value)
            return value
        }

        // 1. 转换成具体类型, 再保存; 2. 同时保存别名的值
        protected Object safeSet(String key, Object value) {
            if (value instanceof Optional) {
                value = value.present ? Optional.ofNullable(ctx.getFieldManager().convert(key, value.get())) : value
            } else {
                value = ctx.getFieldManager().convert(key, value) // 属性值类型转换
            }
            super.put(key, value)

            def n = ctx.getFieldManager().alias(key)
            if (n && n != key) super.put(n, value)
            value
        }

        @Override
        Object put(String key, Object value) {
            ctx.recordData(key, safeSet(key, value))
            value
        }

        @Override
        Object remove(Object key) { // 删除缓存
            def r = super.remove(key)
            def field = ctx.getFieldManager().fieldMap.get(key)
            if (field) {
                super.remove(field.enName)
                super.remove(field.cnName)
                ctx.dataCollectResult.remove(field.dataCollector)
            }
            return r
        }
    }

    /**
     * 执行过的策略
     */
    protected class PassedPolicy {
        // 策略 spec
        transient PolicySpec spec
        // 策略 最终决策
        DecideResult result
        // 策略 属性
        Map<String, Object> attrs
        // 执行策略过程中产生的或用到的数据集
        final Map<String, Object> data = new LinkedHashMap()
        // 策略中执行的规则
        final List<PassedRule> rules = new LinkedList<>()

        @Override
        String toString() {
            return [attrs: attrs, result: result, data: data, rules: rules].toMapString()
        }
    }

    /**
     * 执行过的规则
     */
    protected class PassedRule {
        // 规则名
        transient RuleSpec spec
        // 规则 最终决策
        DecideResult result
        // 规则属性
        Map<String, Object> attrs
        // 执行规则过程中产生的或用到的数据集
        final Map<String, Object> data = new LinkedHashMap()

        @Override
        String toString() {
            return [attrs: attrs, result: result, data: data].toMapString()
        }
    }


    // 日志前缀
    protected String logPrefix() {
        "[${id? "$id, " :''}${decisionHolder.decision.decisionId? "决策: $decisionHolder.spec.决策id" :''}${curPassedPolicy? ", 策略: ${curPassedPolicy.spec.策略名}" :''}${curPassedRule ? ", 规则: ${curPassedRule.spec.规则名}" :''}] -> "
    }


    /**
     * 整条决策 所有信息
     */
    private Map<String, Object> _summary
    Map<String, Object> summary() {
        if (this._summary && end.get()) return this._summary
        //去重复记录(去对应的中文, 保留对应的英文)
        def cleanData = {Map data ->
            data ? data.collect { e ->
                def value = e.value instanceof Optional ? e.value.orElseGet({ null }) : e.value
                def field = fieldManager.fieldMap.get(e.key)
                if (field && field.cnName == e.key) return [field.enName, value]
                return [e.key, value]
            }.collectEntries() : null
        }
        this._summary = [
                id               : id, //执行决策id
                decisionId       : decisionHolder.decision.decisionId, //决策id
                spend            : System.currentTimeMillis() - startup.time, //执行花费时间
                occurTime        : startup, //決策发生时间
                result           : decideResult, //结果
                input            : input, //输入参数
                status           : status, //初始化status: 0001, 结束status: 0000, 错误status: EEEE
                exception        : exception?.toString(), //异常信息
                data             : cleanData(data), //执行过程产生的最终数据集
                detail           : [ //执行详情, 过程详情
                     data    : cleanData(decisionData), //只和决策相关(和具体策略,规则无关)的数据
                     attrs   : decisionHolder.spec.attrs?:null, //决策自定义属性集
                     policies: policies.collect { policy -> //执行过的策略集
                         [
                                 attrs: policy.attrs, result: policy.result, data: cleanData(policy.data), //只和策略相关(和具体规则无关)的数据
                                 rules: policy.rules.collect {rule ->
                                     [attrs: rule.attrs, result: rule.result, data: cleanData(rule.data) /* 和具体规则相关的数据 */]
                                 }
                         ]
                     }
                ],
                dataCollectResult: dataCollectResult?:null // 执行过程中数据收集的结果集
        ]
        this._summary
    }


    /**
     * 用于接口返回
     * @return
     */
    Map<String, Object> result() {
        [
                decideId: id, result: decideResult, decisionId: decisionHolder.decision.decisionId,
                status  : status,
                desc    : exception?.toString(),
                data    : end.get() ? decisionHolder.spec.returnAttrs.collectEntries { name ->
                def v = data.get(name)
                if (v instanceof Optional) {
                    v = v.orElseGet({ null })
                }
                def field = fieldManager.fieldMap.get(name)
                //如果key是中文, 则翻译成对应的英文名
                if (field && field.cnName == name) return [field.enName, v]
                else return [name, v]
            } : null
        ]
    }


    @Override
    String toString() { result().toString() }
}

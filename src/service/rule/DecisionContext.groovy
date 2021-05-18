package service.rule

import cn.xnatural.enet.event.EP
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import service.rule.spec.BaseSpec
import service.rule.spec.DecisionSpec
import service.rule.spec.PolicySpec
import service.rule.spec.RuleSpec
import service.rule.spec.ScorecardSpec

import java.util.concurrent.atomic.AtomicBoolean

/**
 * 决策执行上下文
 */
class DecisionContext {
    public static final Logger log = LoggerFactory.getLogger(DecisionContext)
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
    final List<PassedSpec> policies = new LinkedList<>()
    // 数据key -> 数据收集结果集. 不用 ConcurrentHashMap 因为不能放null值
    final Map<String, Object> dataCollectResult = new LinkedHashMap<>()
    // 过程数据: 决策(非策略,规则)
    final Map<String, Object> decisionData = new LinkedHashMap<>()

    // 是否已启动
    protected final def started = new AtomicBoolean(false)
    // 是否执行结束
    protected final def end = new AtomicBoolean(false)
    // 当前正在执行的策略
    protected PassedSpec curPassedPolicy
    // 当前正在执行的规则
    protected PassedSpec curPassedRule
    // 当前正在执行的评分卡
    protected PassedSpec curPassedScorecard
    // 当前正在执行的子决策
    protected PassedSpec curPassedDecision

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
        trigger()
    }


    /**
     * 触发执行流程: 遍历策略, 遍历规则
     */
    protected final void trigger() {
        if (end.get()) return
        try {
            decideResult = decisionHolder.spec.compute(this)
            end.set(true); decideResult = decideResult?:DecideResult.Accept; status = '0000'
            log.info(logPrefix() + "结束成功. 共执行: " + (summary()['spend']) + "ms. result: "  + result())
            ep?.fire("decision.end", this)
        } catch (Throwable ex) {
            end.set(true); decideResult = DecideResult.Reject; status = 'EEEE'; this.exception = ex
            log.error(logPrefix() + "结束错误. 共执行: " + (summary()['spend']) + "ms. result: " + result(), ex)
            ep?.fire("decision.end", this)
        }
    }


    /**
     * 执行策略
     * @param spec
     * @return
     */
    DecideResult run(PolicySpec spec) {
        curPassedPolicy = new PassedSpec(spec: spec, attrs: [策略名: spec.策略名, *:spec.attrs])
        policies.add(curPassedPolicy)
        log.debug(logPrefix() + "开始执行")
        DecideResult result = spec.compute(this)
        curPassedPolicy.result = result
        log.info(logPrefix() + "结束执行. result: " + result + ", data: " + (curPassedPolicy.data?:null))
        curPassedPolicy = null
        result
    }


    /**
     * 执行规则
     * @param spec
     * @return
     */
    DecideResult run(RuleSpec spec) {
        curPassedRule = new PassedSpec(spec: spec, attrs: [规则名: spec.规则名, *:spec.attrs])
        curPassedPolicy.items.add(curPassedRule)
        log.debug(logPrefix() + "开始执行")
        DecideResult result = spec.compute(this)
        curPassedRule.result = result
        log.info(logPrefix() + "结束执行. result: " + result + ", data: " + (curPassedRule.data?:null))
        curPassedRule = null
        result
    }


    /**
     * 执行评分卡
     * @param spec
     * @return
     */
    def run(ScorecardSpec spec) {
        curPassedScorecard = new PassedSpec(spec: spec, attrs: [评分卡名: spec.评分卡名, *:spec.attrs])
        curPassedPolicy.items.add(curPassedScorecard)
        log.debug(logPrefix() + "开始执行")
        def result = spec.compute(this)
        curPassedScorecard.result = result
        log.info(logPrefix() + "结束执行. result: " + result + ", data: " + (curPassedScorecard.data?:null))
        curPassedScorecard = null
        result
    }


    /**
     * 执行子决策
     * @param spec
     * @return
     */
    DecideResult run(DecisionSpec spec) {
        // 根据 决策id 找到对应的 决策
        DecisionManager dm = ep.fire("bean.get", DecisionManager)
        def holder = dm.findDecision(spec.决策id)
        if (!holder) throw new IllegalArgumentException("决策id($spec.决策id) 未找到对应的决策")

        curPassedDecision = new PassedSpec(spec: holder.spec, attrs: [决策id: spec.决策id, 决策名: holder.spec.决策名, *:holder.spec.attrs])
        curPassedPolicy.items.add(curPassedDecision)
        log.debug(logPrefix() + "开始执行")

        DecideResult result
        DecisionContext ctx = new DecisionContext(id, holder, data, fieldManager, ep)
        if (ctx.started.compareAndSet(false, true)) {
            result = holder.spec.compute(ctx)
            ctx.end.set(true); ctx.status = '0000'
            ctx.result()['data']?.each {setAttr(it.key, it.value)} //设置返回属性
        }

        curPassedDecision.result = result
        log.info(logPrefix() + "结束执行. result: " + result + ", data: " + (curPassedDecision.data?:null) + ", summary: " + ctx.summary())
        curPassedDecision = null
        result
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
     * 当前如果是正在执行评分卡,则记录到评分卡数据
     * 当前如果是正在执行子决策,则记录到子决策数据
     * 当前如果是正在执行策略,则记录到策略数据
     * 当前如果是正在执行决策,则记录到决策数据
     * @param key 属性key
     * @param value 属性值
     */
    protected void recordData(String key, Object value) {
        if (end.get()) return
        if (curPassedRule) curPassedRule.data.put(key, value)
        else if (curPassedScorecard) curPassedScorecard.data.put(key, value)
        else if (curPassedDecision) curPassedDecision.data.put(key, value)
        else if (curPassedPolicy) curPassedPolicy.data.put(key, value)
        else decisionData.put(key, value)
    }


    /**
     * 数据存放. 用于规则闭包执行上下文
     */
    protected class Data extends LinkedHashMap<String, Object> {
        final DecisionContext ctx
        /**
         * 属性值来源: 为了解决优先级,及重新获取的问题
         * 优先级: 执行上下文设置 > input > 收集器(可重复计算)
         */
        final Map<String, String> valueFromMap = new HashMap<>()

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
            // 函数名
            if (ctx.decisionHolder.spec.globalFunctions.containsKey(aName)) return null

            if (!ctx.end.get()) {
                // 属性值已存在
                if (super.containsKey(aName)) {
                    // 判断是否需要重尝试新计算
                    if (valueFromMap.get(aName) == 'collector') {
                        safeSet((String) aName, ctx.getFieldManager().dataCollect(aName.toString(), ctx), 'collector')
                    }
                } else { // 第一次获取属性值
                    safeSet(aName.toString(), null, null) // 占位. 解决 循环属性获取链
                    boolean fromInput = false
                    if (ctx.input) { //数据源1: 先从入参里面获取
                        if (ctx.input.containsKey(aName)) {
                            fromInput = true
                            safeSet((String) aName, ctx.input.get(aName), 'input')
                        } else {
                            def alias = ctx.getFieldManager().alias(aName)
                            if (alias != null && ctx.input.containsKey(alias)) {
                                fromInput = true
                                safeSet((String) aName, ctx.input.get(alias), 'input')
                            }
                        }
                    }
                    if (!fromInput) { //数据源2: 再从数据收集器获取
                        safeSet((String) aName, ctx.getFieldManager().dataCollect(aName.toString(), ctx), 'collector')
                    }
                }
            }
            def value = super.get(aName)
            if (value instanceof Optional) {
                if (value.present) value = value.get()
                else value = null
            }

            ctx.recordData(aName.toString(), value)
            return value
        }

        // 1. 转换成具体类型, 再保存; 2. 同时保存别名的值; 3. 保存值来源
        protected Object safeSet(String key, Object value, String from) {
            if (value instanceof Optional) {
                value = value.present ? Optional.ofNullable(ctx.getFieldManager().convert(key, value.get())) : value
            } else {
                value = ctx.getFieldManager().convert(key, value) // 属性值类型转换
            }
            super.put(key, value)
            valueFromMap.put(key, from)

            def n = ctx.getFieldManager().alias(key)
            if (n && n != key) {
                super.put(n, value)
                valueFromMap.put(n, from)
            }

            value
        }

        @Override
        Object put(String key, Object value) {
            ctx.recordData(key, safeSet(key, value, 'byHand'))
            value
        }

        @Override
        Object remove(Object key) { // 删除缓存
            def r = super.remove(key)
            def field = ctx.getFieldManager().fieldMap.get(key)
            if (field) {
                super.remove(field.enName)
                super.remove(field.cnName)
                // ctx.dataCollectResult.remove(field.dataCollector)
                valueFromMap.remove(field.enName)
                valueFromMap.remove(field.cnName)
            }
            return r
        }
    }


    /**
     * 执行过的spec模块
     */
    protected class PassedSpec {
        // spec
        transient BaseSpec spec
        // 结果
        def result
        // 属性
        Map<String, Object> attrs
        // 执行过程中产生的或用到的数据集
        final Map<String, Object> data = new LinkedHashMap()
        // 子模块
        @Lazy List items = new LinkedList<>()

        @Override
        String toString() {
            return [attrs: attrs, result: result, data: data, items: items].toMapString()
        }
    }


    // 日志前缀
    String logPrefix() {
        "[${id? "$id, " :''}${decisionHolder.decision.decisionId? "决策: $decisionHolder.spec.决策id" :''}${ -> curPassedPolicy? ", 策略: " + curPassedPolicy.spec.name() :''}${ -> curPassedRule ? ", 规则: " + curPassedRule.spec.name() :''}${ -> curPassedScorecard ? ", 评分卡: " + curPassedScorecard.spec.name() :''}${ -> curPassedDecision ?  ", 子决策: " + curPassedDecision.spec.name() + "(" + curPassedDecision.spec.决策id + ")":''}] -> "
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
                                 items: policy.items.collect { item ->
                                     [attrs: item.attrs, result: item.result, data: cleanData(item.data) /* 和具体规则相关的数据 */]
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
                data    : end.get() && decisionHolder.spec.returnAttrs ? decisionHolder.spec.returnAttrs.collectEntries { name ->
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

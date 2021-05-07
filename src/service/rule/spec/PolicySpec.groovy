package service.rule.spec

import cn.xnatural.app.Utils
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer
import service.rule.DecideResult
import service.rule.DecisionContext

/**
 * 策略定义 spec
 */
class PolicySpec extends BaseSpec {
    String 策略名


    void 操作(Closure 操作) {
        nodes << Tuple.tuple('Operate', { DecisionContext ctx ->
            def cl = 操作.rehydrate(ctx.data, 操作, this)
            cl.resolveStrategy = Closure.DELEGATE_FIRST
            cl()
        })
    }


    /**
     * 执行策略条件函数. 条件函数返回false, 则跳出, 继续执行下一个策略
     * @param 条件
     */
    void 条件(Closure 条件) {
        nodes << Tuple.tuple('Condition', { DecisionContext ctx ->
            def cl = 条件.rehydrate(ctx.data, 条件, this)
            cl.resolveStrategy = Closure.DELEGATE_FIRST
            cl()
        })
    }


    void 属性定义(String 属性名, Object 值) {
        if (!属性名) throw new IllegalArgumentException("属性名 不能为空")
        attrs.put(属性名, 值)
    }


    /**
     * 清除属性值缓存
     * @param 属性名
     */
    void 清除(String... 属性名) {
        if (!属性名) return
        nodes << Tuple.tuple('Clear', { DecisionContext ctx ->
            属性名.each {ctx.data.remove(it)}
        })
    }


    /**
     * 根据 dsl 创建规则, 并创建执行
     * @param cl
     */
    void 规则(@DelegatesTo(RuleSpec) Closure cl) {
        RuleSpec spec = new RuleSpec()
        def code = cl.rehydrate(spec, cl, this)
        code.resolveStrategy = Closure.DELEGATE_FIRST
        code()

        if (!spec.规则名) throw new IllegalArgumentException("规则名字不能为空")
        if (!spec.nodes) throw new IllegalArgumentException("$spec.规则名 是空规则")

        if (!spec.enabled) return
        // 定义执行函数
        nodes << Tuple.tuple("Rule", {DecisionContext ctx -> ctx.run(spec)})
    }


    /**
     * 根据 dls 创建 评分卡, 并创建执行
     * @param cl
     */
    void 评分卡(@DelegatesTo(ScorecardSpec) Closure cl) {
        ScorecardSpec spec = new ScorecardSpec()
        def code = cl.rehydrate(spec, cl, this)
        code.resolveStrategy = Closure.DELEGATE_FIRST
        code()

        if (!spec.评分卡名) throw new IllegalArgumentException("评分卡名不能为空")
        if (!spec.模型) throw new IllegalArgumentException("$spec.评分卡名 是空评分卡")

        // 定义执行函数
        nodes << Tuple.tuple("Scorecard", {DecisionContext ctx -> ctx.run(spec)})
    }


    /**
     * 根据 dls 创建 子决策, 并创建执行
     * @param cl
     */
    void 决策(@DelegatesTo(DecisionSpec) Closure cl) {
        DecisionSpec spec = new DecisionSpec()
        def code = cl.rehydrate(spec, cl, this)
        code.resolveStrategy = Closure.DELEGATE_FIRST
        code()

        if (!spec.决策id) throw new IllegalArgumentException("子决策id不能为空")

        // 定义执行函数
        nodes << Tuple.tuple("Decision", {DecisionContext ctx -> ctx.run(spec)})
    }


    /**
     * 计算策略结果
     * @param ctx
     * @return
     */
    DecideResult compute(DecisionContext ctx) {
        DecideResult result
        for (def node : nodes) {
            if ("Rule" == node.v1) {
                result = node.v2.call(ctx)
                if (result && result.block) break
            } else if ("Decision" == node.v1) {
                result = node.v2.call(ctx)
                if (result && result.block) break
            } else if ("Condition" == node.v1) {
                if (!node.v2.call(ctx)) break
            } else if ("Operate" == node.v1) {
                node.v2.call(ctx)
            } else if ("Scorecard" == node.v1) {
                node.v2.call(ctx)
            } else throw new IllegalArgumentException("Unknown type: " + node.v1)
        }
        result
    }

    @Override
    String name() { return 策略名 }


    static PolicySpec of(@DelegatesTo(PolicySpec) Closure cl) {
        def spec = new PolicySpec()
        def code = cl.rehydrate(spec, cl, this)
        code.resolveStrategy = Closure.DELEGATE_FIRST
        code()
        if (!spec.策略名) throw new IllegalArgumentException("策略名字不能为空")
        if (!spec.nodes) throw new IllegalArgumentException("$spec.策略名 是空策略")
        return spec
    }


    /**
     * 根据dsl字符串创建 PolicySpec 对象
     * @param dsl
     * @return PolicySpec 对象
     */
    static PolicySpec of(String dsl) {
        Binding binding = new Binding()
        def config = new CompilerConfiguration()
        def icz = new ImportCustomizer()
        config.addCompilationCustomizers(icz)
        icz.addImports(PolicySpec.name, Utils.name)
        icz.addStarImports(RuleSpec.name, DecisionSpec.name, ScorecardSpec.name)
        def shell = new GroovyShell(Thread.currentThread().contextClassLoader, binding, config)
        shell.evaluate("service.rule.spec.PolicySpec.of{$dsl}")
    }
}

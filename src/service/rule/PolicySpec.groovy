package service.rule

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer

/**
 * 策略定义 spec
 */
class PolicySpec {
    String                         策略名
    /**
     * 规则集
     */
    final List<RuleSpec> rules = new LinkedList<>()
    /**
     * 自定义属性集
     */
    @Lazy Map<String, Object> attrs = new HashMap<>()
    /**
     * 顺序函数: 条件, 操作, 清除
     */
    @Lazy List<Tuple2<String, Closure>> fns = new LinkedList<>()


    void 操作(Closure 操作) {
        fns << Tuple.tuple('Operate', { Map ctx ->
            def cl = 操作.rehydrate(ctx, 操作, this)
            cl.resolveStrategy = Closure.DELEGATE_FIRST
            cl()
            null
        })
    }


    /**
     * 执行策略条件函数. 条件函数返回false, 则跳出, 继续执行下一个策略
     * @param 条件
     */
    PolicySpec 条件(Closure 条件) {
        fns << Tuple.tuple('Condition', { Map ctx ->
            def cl = 条件.rehydrate(ctx, 条件, this)
            cl.resolveStrategy = Closure.DELEGATE_FIRST
            if (cl()) return true
            else return false
        })
        this
    }


    PolicySpec 属性定义(String 属性名, Object 值) {
        if (!属性名) throw new IllegalArgumentException("属性名 不能为空")
        attrs.put(属性名, 值)
        this
    }


    /**
     * 清除属性值缓存
     * @param 属性名
     */
    void 清除(String... 属性名) {
        if (!属性名) return
        fns << Tuple.tuple('Clear', { Map ctx ->
            属性名.each {ctx.remove(it)}
            null
        })
    }


    PolicySpec 规则定义(@DelegatesTo(RuleSpec) Closure cl) {
        RuleSpec rule = new RuleSpec(); rules.add(rule)
        def code = cl.rehydrate(rule, cl, this)
        code.resolveStrategy = Closure.DELEGATE_FIRST
        code()
        this
    }


    static PolicySpec of(@DelegatesTo(PolicySpec) Closure cl) {
        def p = new PolicySpec()
        def code = cl.rehydrate(p, cl, this)
        code.resolveStrategy = Closure.DELEGATE_FIRST
        code()
        return p
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
        icz.addImports(PolicySpec.class.name)
        icz.addStarImports(RuleSpec.class.name)
        def shell = new GroovyShell(Thread.currentThread().contextClassLoader, binding, config)
        shell.evaluate("service.rule.PolicySpec.of{$dsl}")
    }
}

package service.rule.spec

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer

/**
 * 策略定义 spec
 */
class PolicySpec {
    String                         策略名
    protected final List<RuleSpec> rules = new LinkedList<>()
    @Lazy Map<String, Object> attrs = new HashMap<>()
    // 进入条件函数
    protected Closure condition
    // 预操作函数
    protected Closure operateFn


    void 操作(Closure 操作) {
        operateFn = { Map ctx ->
            def cl = 操作.rehydrate(ctx, 操作, this)
            cl.resolveStrategy = Closure.DELEGATE_FIRST
            cl()
            null
        }
    }


    /**
     * 条件为 false 则不执行此条策略
     * @param cl
     * @return
     */
    PolicySpec 条件(Closure cl) {
        condition = {Map ctx ->
            def fn = cl.rehydrate(ctx, cl, this)
            if (fn()) return true
            return false
        }
        this
    }


    PolicySpec 属性定义(String 属性名, Object 值) {
        if (!属性名) throw new IllegalArgumentException("属性名 不能为空")
        attrs.put(属性名, 值)
        this
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
        shell.evaluate("service.rule.spec.PolicySpec.of{$dsl}")
    }
}

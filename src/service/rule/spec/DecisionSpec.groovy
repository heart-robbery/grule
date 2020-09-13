package service.rule.spec

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer

/**
 * 决策 DSL
 */
class DecisionSpec {
    String 决策id
    String 决策名
    String 决策描述

    // 策略集: 顺序执行
    @Lazy List<PolicySpec> policies    = new LinkedList<>()
    @Lazy Set<String>      returnAttrs = new LinkedHashSet<>()
    @Lazy Map<String, Object> attrs = new HashMap<>()


    DecisionSpec 属性定义(String 属性名, Object 值) {
        if (!属性名) throw new IllegalArgumentException("属性名 不能为空")
        attrs.put(属性名, 值)
        this
    }


    DecisionSpec 策略定义(@DelegatesTo(PolicySpec) Closure cl) {
        PolicySpec policy = new PolicySpec(); policies.add(policy)
        def code = cl.rehydrate(policy, cl, this)
        code.resolveStrategy = Closure.DELEGATE_FIRST
        code()
        this
    }


    DecisionSpec 返回属性(String 属性名) { returnAttrs.add(属性名); this }


    static DecisionSpec of(@DelegatesTo(DecisionSpec) Closure cl) {
        def p = new DecisionSpec()
        def code = cl.rehydrate(p, cl, this)
        code.resolveStrategy = Closure.DELEGATE_FIRST
        code()
        return p
    }


    /**
     * 根据dsl字符串创建 DecisionSpec 对象
     * @param dsl dsl 字符串
     * @return DecisionSpec
     */
    static DecisionSpec of(String dsl) {
        Binding binding = new Binding()
        def config = new CompilerConfiguration()
        def icz = new ImportCustomizer()
        config.addCompilationCustomizers(icz)
        icz.addImports(DecisionSpec.class.name)
        def shell = new GroovyShell(Thread.currentThread().contextClassLoader, binding, config)
        shell.evaluate("service.rule.spec.DecisionSpec.of{$dsl}")
    }
}

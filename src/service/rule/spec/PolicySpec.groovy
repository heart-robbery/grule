package service.rule.spec

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer

/**
 * 策略定义 spec
 */
class PolicySpec {
    String                         策略id
    String                         策略名
    String                         策略描述
    protected final List<RuleSpec> rules = new LinkedList<>()


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


    PolicySpec 规则定义(@DelegatesTo(RuleSpec) Closure cl) {
        RuleSpec rule = new RuleSpec(); rules.add(rule)
        def code = cl.rehydrate(rule, cl, this)
        code.resolveStrategy = Closure.DELEGATE_FIRST
        code()
        this
    }
}

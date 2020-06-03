package service.rule.spec

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer

class PolicySpec {
    String 策略名
    String 策略描述
    protected final List<RuleSpec> rs = new LinkedList<>()


    static PolicySpec of(@DelegatesTo(PolicySpec) Closure cl) {
        def p = new PolicySpec()
        def code = cl.rehydrate(p, cl, this)
        code.resolveStrategy = Closure.DELEGATE_FIRST
        code()
        return p
    }

    static PolicySpec of(String dsl) {
        Binding binding = new Binding()
        def config = new CompilerConfiguration()
        def icz = new ImportCustomizer()
        config.addCompilationCustomizers(icz)
//        icz.addImports(PolicySpec.class.name)
//        icz.addStarImports(RuleSpec.class.name)
        def shell = new GroovyShell(binding, config)
        shell.evaluate("service.rule.spec.PolicySpec.of{$dsl}")
    }


    PolicySpec 规则定义(@DelegatesTo(value = RuleSpec, strategy = Closure.DELEGATE_FIRST) Closure cl) {
        RuleSpec rule = new RuleSpec(); rs.add(rule)
        def code = cl.rehydrate(cl, rule, this)
        cl.resolveStrategy = Closure.DELEGATE_FIRST
        code()
        this
    }
}

package sevice.rule.spec

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer

class PolicySetSpec {
    String               策略集名
    String               策略集id
    String               策略集描述

    @Lazy List<String> ps = new LinkedList<>()
    @Lazy List<String> returnAttrs = new LinkedList<>()


    PolicySetSpec 执行策略(String 策略名) { ps.add(策略名); this }

    PolicySetSpec 返回属性(String 属性名) { returnAttrs.add(属性名); this }


    static PolicySetSpec of(@DelegatesTo(PolicySetSpec) Closure cl) {
        def p = new PolicySetSpec()
        def code = cl.rehydrate(p, this, this)
        code.resolveStrategy = Closure.DELEGATE_FIRST
        code()
        return p
    }


    static PolicySetSpec of(String dsl) {
        Binding binding = new Binding()
        def config = new CompilerConfiguration()
        def icz = new ImportCustomizer()
        config.addCompilationCustomizers(icz)
        icz.addImports(PolicySetSpec.class.name)
        // icz.addStarImports(RuleSpec.class.name)
        def shell = new GroovyShell(binding, config)
        shell.evaluate("PolicySetSpec.of{$dsl}")
    }
}

package service.rule.spec

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer

class DecisionSpec {
    String 决策id
    String 决策描述

    @Lazy List<String> ps = new LinkedList<>()
    @Lazy Set<String> returnAttrs = new LinkedHashSet<>()


    DecisionSpec 添加策略(String 策略名) { ps.add(策略名); this }

    DecisionSpec 返回属性(String 属性名) { returnAttrs.add(属性名); this }


    static DecisionSpec of(@DelegatesTo(DecisionSpec) Closure cl) {
        def p = new DecisionSpec()
        def code = cl.rehydrate(p, this, this)
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

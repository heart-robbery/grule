package service.rule.spec

import cn.xnatural.app.Utils
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.lang.reflect.Array

/**
 * 决策 DSL
 */
class DecisionSpec {
    String 决策id
    String 决策名
    String 决策描述

    /**
     * 策略集: 顺序执行
     */
    @Lazy List<PolicySpec> policies    = new LinkedList<>()
    /**
     * 接口返回属性集
     */
    @Lazy Set<String>      returnAttrs = new LinkedHashSet<>()
    /**
     * 自定义属性
     */
    @Lazy Map<String, Object> attrs = new HashMap<>()
    /**
     * 自定义函数
     */
    final Map<String, Closure> functions = new HashMap<>()
    // 预操作函数
    protected Closure operateFn

    DecisionSpec() {
        Logger log = LoggerFactory.getLogger("ROOT")
        functions.put("INFO", {String msg -> if (msg) log.info(msg.toString()) })
    }

    void 操作(Closure 操作) {
        operateFn = { Map ctx ->
            def cl = 操作.rehydrate(ctx, 操作, this)
            cl.resolveStrategy = Closure.DELEGATE_FIRST
            cl()
            null
        }
    }


    DecisionSpec 函数定义(String 函数名, Closure 函数) {
        if (!函数名) throw new IllegalArgumentException("函数名 不能为空")
        if (!函数) throw new IllegalArgumentException("函数 不能为空")
        functions.put(函数名, 函数)
        this
    }


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


    /**
     * 函数拦截 调用
     * @param name 函数名
     * @param args 参数
     * @return
     */
    def methodMissing(String name, def args) {
        def fn = functions.get(name)
        if (fn) {
            int length = Array.getLength(args)
            if (length == 0) return fn()
            else if (length == 1) return fn(Array.get(args, 0))
            else if (length == 2) return fn(Array.get(args, 0), Array.get(args, 1))
            else if (length == 3) return fn(Array.get(args, 0), Array.get(args, 1), Array.get(args, 2))
            else if (length == 4) return fn(Array.get(args, 0), Array.get(args, 1), Array.get(args, 2), Array.get(args, 3))
            else if (length == 5) return fn(Array.get(args, 0), Array.get(args, 1), Array.get(args, 2), Array.get(args, 3), Array.get(args, 4))
            else throw new Exception("Custom function '$name' args length: " + length)
        }
        else throw new MissingMethodException(name, DecisionSpec, args)
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
        icz.addImports(DecisionSpec.class.name, JSON.class.name, JSONObject.class.name, Utils.class.name)
        def shell = new GroovyShell(Thread.currentThread().contextClassLoader, binding, config)
        shell.evaluate("service.rule.spec.DecisionSpec.of{$dsl}")
    }
}

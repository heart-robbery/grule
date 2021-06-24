package service.rule.spec

import cn.xnatural.app.Utils
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import service.rule.DecideResult
import service.rule.DecisionContext

import java.lang.reflect.Array

/**
 * 决策 DSL
 */
class DecisionSpec extends BaseSpec {
    String 决策id
    String 决策名
    String 决策描述

    /**
     * 接口返回属性集
     */
    @Lazy Set<String> returnAttrs = new LinkedHashSet<>()
    /**
     * 全局决策内自定义函数
     */
    @Lazy Map<String, Closure> globalFunctions = new HashMap<>()


    DecisionSpec() {
        Logger log = LoggerFactory.getLogger("ROOT")
        globalFunctions.put("INFO", { String msg -> if (msg) log.info(msg.toString()) })
        globalFunctions.put("ERROR", { String msg, Exception ex -> if (msg) log.error(msg.toString(), ex) })
    }


    void 操作(Closure 操作) {
        nodes << Tuple2.tuple("Operate", { DecisionContext ctx ->
            def cl = 操作.rehydrate(ctx.data, 操作, this)
            cl.resolveStrategy = Closure.DELEGATE_FIRST
            cl()
        })
    }


    DecisionSpec 函数定义(String 函数名, Closure 函数) {
        if (!函数名) throw new IllegalArgumentException("函数名 不能为空")
        if (!函数) throw new IllegalArgumentException("函数 不能为空")
        globalFunctions.put(函数名, 函数)
        this
    }


    DecisionSpec 属性定义(String 属性名, Object 值) {
        if (!属性名) throw new IllegalArgumentException("属性名 不能为空")
        attrs.put(属性名, 值)
        this
    }


    DecisionSpec 返回属性(String 属性名) { returnAttrs.add(属性名); this }


    /**
     * 根据 dsl 创建 策略, 并创建执行
     * @param cl
     */
    void 策略(@DelegatesTo(PolicySpec) Closure cl) {
        PolicySpec spec = new PolicySpec()
        def code = cl.rehydrate(spec, cl, this)
        code.resolveStrategy = Closure.DELEGATE_FIRST
        code()
        // 定义执行函数
        nodes << Tuple.tuple("Policy", {DecisionContext ctx -> ctx.run(spec)})
    }


    /**
     * 执行决策
     * @param ctx
     * @return
     */
    DecideResult compute(DecisionContext ctx) {
        for (def node : nodes) {
            if ("Policy" == node.v1) {
                DecideResult result = node.v2.call(ctx)
                if (result && result.block) return result
            } else if ("Operate" == node.v1) {
                DecisionContext.log.trace(ctx.logPrefix() + "[操作]开始执行")
                node.v2.call(ctx)
            } else throw new IllegalArgumentException("Unknown type: " + node.v1)
        }
        DecideResult.Accept
    }


    @Override
    String name() { return 决策名 }


    /**
     * 函数拦截 调用
     * @param name 函数名
     * @param args 参数
     */
    def methodMissing(String name, def args) {
        def fn = globalFunctions.get(name)
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

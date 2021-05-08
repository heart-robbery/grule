package service.rule.spec

import service.rule.DecideResult
import service.rule.DecisionContext

/**
 * 规则定义 spec
 */
class RuleSpec extends BaseSpec {
    protected boolean enabled = true
    String 规则名


    void 启用() {enabled = true}


    void 关闭() {enabled = false}


    /**
     * 自定义属性
     * @param 属性名
     * @param 值
     */
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


    void 拒绝(Closure<Boolean> 条件) {
        nodes << Tuple.tuple('Reject', { DecisionContext ctx ->
            def cl = 条件.rehydrate(ctx.data, 条件, this)
            if (cl()) return DecideResult.Reject
            null
        })
    }


    void 通过(Closure<Boolean> 条件) {
        nodes << Tuple.tuple('Accept', { DecisionContext ctx ->
            def cl = 条件.rehydrate(ctx.data, 条件, this)
            if (cl()) return DecideResult.Accept
            null
        })
    }


    void 人工审核(Closure<Boolean> 条件) {
        nodes << Tuple.tuple('Review', { DecisionContext ctx ->
            def cl = 条件.rehydrate(ctx.data, 条件, this)
            if (cl()) return DecideResult.Review
            null
        })
    }


    void 操作(Closure 操作) {
        nodes << Tuple.tuple('Operate', { DecisionContext ctx ->
            def cl = 操作.rehydrate(ctx.data, 操作, this)
            cl.resolveStrategy = Closure.DELEGATE_FIRST
            cl()
        })
    }


    /**
     * 计算规则结果
     * @param ctx
     * @return
     */
    DecideResult compute(DecisionContext ctx) {
        DecideResult result
        for (def node : nodes) {
            if ("Reject" == node.v1) {
                result = node.v2.call(ctx)
                if (result && result.block) break
            } else if ("Accept" == node.v1) {
                result = node.v2.call(ctx)
                if (result && result.block) break
            } else if ("Review" == node.v1) {
                result = node.v2.call(ctx)
                if (result && result.block) break
            } else if ("Clear" == node.v1) {
                node.v2.call(ctx)
            } else if ("Operate" == node.v1) {
                node.v2.call(ctx)
            } else throw new IllegalArgumentException("Unknown type: " + node.v1)
        }
        result
    }

    @Override
    String name() { return 规则名 }
}

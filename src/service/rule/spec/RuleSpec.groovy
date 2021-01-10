package service.rule.spec

import service.rule.DecisionEnum

/**
 * 规则定义 spec
 */
class RuleSpec {
    private boolean enabled = true
    String 规则名
    @Lazy private List<Tuple2<String, Closure>> decisionFn = new LinkedList<>()
    // 自定义规则属性. 例: 自定义id, 描述
    @Lazy Map<String, Object> attrs = new HashMap<>()


    void 启用() {enabled = true}


    void 关闭() {enabled = false}


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
        decisionFn << Tuple.tuple('Clear', { Map ctx ->
            属性名.each {ctx.remove(it)}
            null
        })
    }


    void 拒绝(Closure<Boolean> 条件) {
        decisionFn << Tuple.tuple('Reject', { Map ctx ->
            def cl = 条件.rehydrate(ctx, 条件, this)
            if (cl()) return DecisionEnum.Reject
            null
        })
    }


    void 通过(Closure<Boolean> 条件) {
        decisionFn << Tuple.tuple('Accept', { Map ctx ->
            def cl = 条件.rehydrate(ctx, 条件, this)
            if (cl()) return DecisionEnum.Accept
            null
        })
    }


    void 人工审核(Closure<Boolean> 条件) {
        decisionFn << Tuple.tuple('Review', { Map ctx ->
            def cl = 条件.rehydrate(ctx, 条件, this)
            if (cl()) return DecisionEnum.Review
            null
        })
    }


    void 操作(Closure 操作) {
        decisionFn << Tuple.tuple('Operate', { Map ctx ->
            def cl = 操作.rehydrate(ctx, 操作, this)
            cl.resolveStrategy = Closure.DELEGATE_FIRST
            cl()
            null
        })
    }
}

package service.rule.spec

import service.rule.Decision

/**
 * 规则定义 spec
 */
class RuleSpec {
    private boolean enabled = true
            String                        规则名
            String                        规则id
            String                        规则描述
    @Lazy private List<Tuple2<String, Closure>> decisionFn = new LinkedList<>()


    def 启用() {enabled = true}


    def 关闭() {enabled = false}


    def 拒绝(Closure<Boolean> 条件) {
        decisionFn << Tuple.tuple('Reject', { Map ctx ->
            def cl = 条件.rehydrate(ctx, 条件, this)
            if (cl()) return Decision.Reject
            null
        })
    }


    def 通过(Closure<Boolean> 条件) {
        decisionFn << Tuple.tuple('Accept', { Map ctx ->
            def cl = 条件.rehydrate(ctx, 条件, this)
            if (cl()) return Decision.Accept
            null
        })
    }


    def 人工审核(Closure<Boolean> 条件) {
        decisionFn << Tuple.tuple('Review', { Map ctx ->
            def cl = 条件.rehydrate(ctx, 条件, this)
            if (cl()) return Decision.Review
            null
        })
    }


    def 操作(Closure 操作) {
        decisionFn << Tuple.tuple('Operate', { Map ctx ->
            def cl = 操作.rehydrate(ctx, 操作, this)
            cl.resolveStrategy = Closure.DELEGATE_FIRST
            cl()
            null
        })
    }
}

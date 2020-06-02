package sevice.rule.spec

import sevice.rule.Decision

class RuleSpec {
    private boolean enabled = true
            String                        规则名
            String                        规则id
            String                        规则描述
    private @Lazy Map<String, Closure> decisionFn = new LinkedHashMap<>()


    def 启用() {enabled = true}

    def 关闭() {enabled = false}

    def 拒绝(Closure<Boolean> 条件) {
        decisionFn.put('Reject', { Map ctx ->
            条件 = 条件.rehydrate(ctx, 条件, ctx)
            if (条件()) return Decision.Reject
            null
        })
    }

    def 通过(Closure<Boolean> 条件) {
        decisionFn.put('Accept', { Map ctx ->
            条件 = 条件.rehydrate(ctx, 条件, ctx)
            if (条件()) return Decision.Accept
            null
        })
    }

    def 人工审核(Closure<Boolean> 条件) {
        decisionFn.put('Review', { Map ctx ->
            条件 = 条件.rehydrate(ctx, 条件, ctx)
            if (条件()) return Decision.Review
            null
        })
    }

    def 操作(Closure 操作) {
        decisionFn.put('Operate', { Map ctx ->
            操作.resolveStrategy = Closure.DELEGATE_FIRST
            操作.delegate = ctx
            操作()
            null
        })
    }
}

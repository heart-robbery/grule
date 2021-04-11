package service.rule
/**
 * 规则定义 spec
 */
class RuleSpec {
    protected boolean enabled = true
    String 规则名
    /**
     * 规则函数集
     */
    @Lazy List<Tuple2<String, Closure>> fns = new LinkedList<>()
    /**
     * 自定义规则属性. 例: 自定义id, 描述
     */
    @Lazy Map<String, Object> attrs = new HashMap<>()


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
        fns << Tuple.tuple('Clear', { Map ctx ->
            属性名.each {ctx.remove(it)}
            null
        })
    }


    void 拒绝(Closure<Boolean> 条件) {
        fns << Tuple.tuple('Reject', { Map ctx ->
            def cl = 条件.rehydrate(ctx, 条件, this)
            if (cl()) return DecideResult.Reject
            null
        })
    }


    void 通过(Closure<Boolean> 条件) {
        fns << Tuple.tuple('Accept', { Map ctx ->
            def cl = 条件.rehydrate(ctx, 条件, this)
            if (cl()) return DecideResult.Accept
            null
        })
    }


    void 人工审核(Closure<Boolean> 条件) {
        fns << Tuple.tuple('Review', { Map ctx ->
            def cl = 条件.rehydrate(ctx, 条件, this)
            if (cl()) return DecideResult.Review
            null
        })
    }


    void 操作(Closure 操作) {
        fns << Tuple.tuple('Operate', { Map ctx ->
            def cl = 操作.rehydrate(ctx, 操作, this)
            cl.resolveStrategy = Closure.DELEGATE_FIRST
            cl()
            null
        })
    }
}

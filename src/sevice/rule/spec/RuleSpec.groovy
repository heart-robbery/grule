package sevice.rule.spec
import sevice.rule.Decision
import sevice.rule.RuleContext

class RuleSpec {
    private boolean enabled = true
            String                        规则名
            String                        规则id
            String                        规则描述
    private @Lazy Map<String, Closure> decisionFn = new LinkedHashMap<>()


    def 启用() {enabled = true}

    def 关闭() {enabled = false}

    def 拒绝(Closure<Boolean> 条件) {
        decisionFn.put('Reject', { RuleContext ctx ->
//            条件.resolveStrategy = Closure.DELEGATE_FIRST
//            条件.delegate = ctx
            条件 = 条件.rehydrate(ctx, ctx, ctx)
            if (条件.call()) return Decision.Reject
            null
        })
    }

    def 通过(Closure<Boolean> 条件) {
        decisionFn.put('Accept', { RuleContext ctx ->
//            条件.resolveStrategy = Closure.DELEGATE_FIRST
//            条件.delegate = ctx
            条件 = 条件.rehydrate(ctx, 条件, ctx)
            if (条件.call()) return Decision.Accept
            null
        })
    }

    def 人工审核(Closure<Boolean> 条件) {
        decisionFn.put('Review', { RuleContext ctx ->
//            条件.resolveStrategy = Closure.DELEGATE_FIRST
//            条件.delegate = ctx
            条件 = 条件.rehydrate(ctx, 条件, ctx)
            if (条件.call()) return Decision.Review
            null
        })
    }

    def 赋值操作(Closure 操作) {
        decisionFn.put('Operate-set', { RuleContext ctx ->
//            def cl = 操作.rehydrate(ctx, 操作, this)
//            cl.resolveStrategy = Closure.DELEGATE_FIRST
//            cl.call()
//            操作.resolveStrategy = Closure.DELEGATE_FIRST
//            操作.delegate = ctx
            操作 = 操作.rehydrate(ctx, 操作, ctx)
            操作.call()
            null
        })
    }

//    def 跳到规则(String 目标规则id, 条件) {
//        decisionFn.add{ExecutionContext ctx ->
//            条件.resolveStrategy = Closure.DELEGATE_FIRST
//            条件.delegate = ctx
//            if (条件.call()) ctx.nextCustomId = 目标规则id
//            null
//        }
//    }


//    def 列表定义(String...ss) {
//        if (!ss || ss.length < 2) throw new IllegalArgumentException("列表定义: 列表名, 值1, 值2...")
//        Set<String> ls
//        ss.eachWithIndex {s,i ->
//            if (i == 0) {
//                ls = customList.get(s)
//                if (ls) throw new IllegalArgumentException("列表被覆盖. " + s)
//                ls = customList.computeIfAbsent(s, {new LinkedHashSet<>()})
//            } else {
//                ls.add(s)
//            }
//        }
//    }
//
//    boolean 在列表中(String 列表名, String 值) {customList.get(列表名)?.contains(值)}
//
//    boolean 不在列表中(String 列表名, String 值) {
//        def ls = customList.get(列表名)
//        if (ls?.contains(值)) return true
//        false
//    }
//
//    static boolean 性别是否为男(String 身份证号) {
//        if (身份证号.length() >= 18) {
//            return Integer.parseInt(身份证号.substring(16, 17)) % 2 != 0
//        }
//        false
//    }
//
//    static boolean 性别是否为女(String 身份证号) {
//        if (身份证号.length() >= 18) {
//            return Integer.parseInt(身份证号.substring(16, 17)) % 2 == 0
//        }
//        false
//    }
//
//    static int 计算年龄(String 身份证号) {
//        int iAge = 0
//        Calendar cal = Calendar.getInstance()
//        String year = 身份证号.substring(6, 10)
//        int iCurrYear = cal.get(Calendar.YEAR)
//        iAge = iCurrYear - Integer.valueOf(year)
//        return iAge
//    }
//
//    static boolean 或者(boolean... 条件列表) { 条件列表?.find {it} }
//
//    static boolean 并且(boolean... 条件列表) { 条件列表?.every {it}}
//
//    static boolean 包含(String 值1, String 值2) {值1?.contains(值2)}
//
//    static boolean 不包含(String 值1, String 值2) {值1?.contains(值2)}
//
//    static boolean 等于(Object 值1, Object 值2) {值1 == 值2}
//
//    static boolean 不等于(Object 值1, Object 值2) {值1 != 值2}
//
//    static boolean 大于(Comparable 值1, Comparable 值2) {值1 > 值2}
//
//    static boolean 小于(Comparable 值1, Comparable 值2) {值1 < 值2}
//
//    static boolean 大于等于(Comparable 值1, Comparable 值2) {值1 >= 值2}
//
//    static boolean 小于等于(Comparable 值1, Comparable 值2) {值1 <= 值2}

}

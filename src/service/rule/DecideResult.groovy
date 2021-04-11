package service.rule

/**
 * 决策结果
 */
enum DecideResult {
    Accept("通过", false), Reject("拒绝", true), Review("人工审核", false)
    String cn
    /**
     * 是否阻断往下执行
     */
    boolean block

    DecideResult(String cn, boolean block) {
        this.cn = cn
        this.block = block
    }
}
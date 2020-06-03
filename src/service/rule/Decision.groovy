package service.rule

/**
 * 决策结果
 */
enum Decision {
    Accept("通过"), Reject("拒绝"), Review("人工审核")
    String cn

    Decision(String cn) {
        this.cn = cn
    }
}
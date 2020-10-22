package service.rule

/**
 * 决策结果
 */
enum DecisionEnum {
    Accept("通过"), Reject("拒绝"), Review("人工审核")
    String cn

    DecisionEnum(String cn) {
        this.cn = cn
    }
}
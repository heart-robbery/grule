package service.rule

import entity.Decision
import service.rule.spec.DecisionSpec

import java.util.function.Function

/**
 * 决策 Holder
 */
class DecisionHolder {
    // 对应实体 decision
    Decision decision
    // dsl spec
    DecisionSpec spec
    // 参数验证函数
    Function<Map<String, Object>, Boolean> paramValidator
}

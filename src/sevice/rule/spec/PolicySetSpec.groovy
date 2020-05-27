package sevice.rule.spec

class PolicySetSpec {
    String               策略集名
    String               策略集描述

    @Lazy List<PolicySpec> ps = new LinkedList<>()

    PolicySetSpec 添加策略(PolicySpec 策略) { ps.add(策略); this }
}

package dao.entity

import core.jpa.BaseEntity
import org.hibernate.annotations.Type

import javax.persistence.*

/**
 * 决策结果保存地
 */
@Entity
class DecisionResult extends BaseEntity {
    @Id
    String id
    /**
     * 决策id
     */
    String decisionId
    /**
     * 决策结果
     */
    @Enumerated(EnumType.STRING)
    service.rule.Decision decision
    /**
     * 决策时间
     */
    Date occurTime
    /**
     * 执行花费时间
     */
    Long spend
    /**
     * 异常信息
     */
    String exception
    /**
     * 身份证
     */
    String idNum
    /**
     * 输入参数 json 字符串
     */
    @Column(length = 800)
    String input
    /**
     * 属性集 json 字符串
     */
    @Lob
    @Basic
    @Type(type = "text")
    String attrs
    /**
     * 执行的规则集
     */
    @Lob
    @Basic
    @Type(type = "text")
    String rules
    /**
     * 数据收集结果集
     */
    @Lob
    @Basic
    @Type(type = "text")
    String dataCollectResult
}

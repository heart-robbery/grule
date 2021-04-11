package entity


import cn.xnatural.jpa.IEntity
import org.hibernate.annotations.Type
import service.rule.DecisionEnum

import javax.persistence.*

/**
 * 决策结果保存地
 */
@Entity
@Table(indexes = [@Index(name = "idx_occurTime", columnList = "occurTime")])
class DecisionResult implements IEntity {
    /**
     * 流水id
     */
    @Id
    String       id
    /**
     * 决策id
     */
    String       decisionId
    /**
     * 执行结果状态
     */
    String       status
    /**
     * 决策结果
     */
    @Enumerated(EnumType.STRING)
    DecisionEnum decision
    /**
     * 决策时间
     */
    @Column(nullable = false)
    Date         occurTime
    /**
     * 执行花费时间
     */
    Long         spend
    /**
     * 异常信息
     */
    @Column(length = 1000)
    String       exception
    /**
     * 关键属性
     */
    String       keyProp
    /**
     * 输入参数 json 字符串
     */
    @Lob
    @Basic
    @Type(type = "text")
    String       input
    /**
     * 属性集 json 字符串
     */
    @Lob
    @Basic
    @Type(type = "text")
    String       attrs
    /**
     * 执行的规则集
     */
    @Lob
    @Basic
    @Type(type = "text")
    String       rules
    /**
     * 数据收集结果集
     */
    @Lob
    @Basic
    @Type(type = "text")
    String dataCollectResult
}

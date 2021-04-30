package entity


import cn.xnatural.jpa.IEntity
import org.hibernate.annotations.Type
import service.rule.DecideResult

import javax.persistence.*

/**
 * 决策记录
 */
@Entity
@Table(indexes = [@Index(name = "idx_occurTime", columnList = "occurTime")])
class DecideRecord implements IEntity {
    /**
     * 决策流水id
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
    DecideResult result
    /**
     * 决策时间
     */
    @Column(nullable = false)
    Date         occurTime
    /**
     * 执行花费时间
     */
    Integer      spend
    /**
     * 异常信息
     */
    @Column(length = 1000)
    String       exception
    /**
     * 输入参数 json 字符串
     */
    @Lob
    @Basic
    @Type(type = "text")
    String       input
    /**
     * 最终数据集
     */
    @Lob
    @Basic
    @Type(type = "text")
    String       data
    /**
     * 执行过程描述详情
     */
    @Lob
    @Basic
    @Type(type = "text")
    String       detail
    /**
     * 数据收集结果集
     */
    @Lob
    @Basic
    @Type(type = "text")
    String dataCollectResult
}

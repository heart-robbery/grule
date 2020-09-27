package dao.entity

import core.jpa.LongIdEntity
import org.hibernate.annotations.Type

import javax.persistence.*

/**
 * 决策结果保存地
 */
@Entity
@Table(indexes = [@Index(name = "index_collectDate", columnList = "collectDate"), @Index(name = "index_decideId", columnList = "decideId"),
    @Index(name = "index_collector", columnList = "collector")])
class CollectResult extends LongIdEntity {
    /**
     * 执行决策id
     */
    String decideId
    /**
     * 决策id
     */
    String decisionId
    /**
     * 收集器
     */
    String collector
    /**
     * 收集器:类型
     */
    String collectorType
    /**
     * 收集时间
     */
    Date collectDate
    /**
     * 执行时长
     */
    Long spend
    /**
     * http 调用 url
     */
    @Column(length = 500)
    String url
    /**
     * http 请求 body
     */
    @Column(length = 1000)
    String body
    /**
     * 收集的结果
     */
    @Lob
    @Basic
    @Type(type = "text")
    String result
    /**
     * 结果解析之后
     */
    @Lob
    @Basic
    @Type(type = "text")
    String resolveResult
    /**
     * http 请求异常
     */
    @Column(length = 1000)
    String httpException
    /**
     * 解析http返回异常
     */
    @Column(length = 1000)
    String parseException
    /**
     * 脚本执行异常
     */
    @Column(length = 1000)
    String scriptException
}

package dao.entity

import core.jpa.LongIdEntity
import org.hibernate.annotations.Type

import javax.persistence.Basic
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Lob

/**
 * 决策结果保存地
 */
@Entity
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
}

package entity

import cn.xnatural.jpa.IEntity
import org.hibernate.annotations.Type

import javax.persistence.*

/**
 * 决策结果保存地
 */
@Entity
@Table(indexes = [@Index(name = "idx_collectDate", columnList = "collectDate")])
class CollectRecord implements IEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id
    /**
     * 执行决策流水id
     */
    String decideId
    /**
     * 决策id
     */
    String decisionId
    /**
     * 收集器id
     */
    String collector
    /**
     * 收集器:类型
     */
    String collectorType
    /**
     * 执行结果状态
     */
    String status
    /**
     * http是否查得(数据是否成功)
     */
    String dataStatus
    /**
     * 收集时间
     */
    Date   collectDate
    /**
     * 执行时长
     */
    Long   spend
    /**
     * 是否取的缓存
     */
    Boolean cache
    /**
     * http 调用 url
     */
    @Column(length = 1000)
    String url
    /**
     * http 请求 body
     */
    @Lob
    @Basic
    @Type(type = "text")
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
    @Lob
    @Basic
    @Type(type = "text")
    String httpException
    /**
     * 解析http返回异常
     */
    @Lob
    @Basic
    @Type(type = "text")
    String parseException
    /**
     * 脚本执行异常
     */
    @Lob
    @Basic
    @Type(type = "text")
    String scriptException
}

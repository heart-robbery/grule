package entity

import cn.xnatural.jpa.IEntity
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.Type

import javax.persistence.*

/**
 * 决策结果保存地
 */
@Entity
@Table(indexes = [
        @Index(name = "idx_collectDate", columnList = "collectDate"),
        @Index(name = "idx_decideId", columnList = "decideId")
])
@DynamicUpdate
class CollectRecord implements IEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id
    /**
     * 执行决策流水id
     */
    @Column(length = 36)
    String decideId
    /**
     * 决策id {@link Decision#id}
     */
    @Column(length = 36)
    String decisionId
    /**
     * 收集器id {@link DataCollector#id}
     */
    String collector
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
    @Column(nullable = false)
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
     * 收集器执行异常
     */
    @Lob
    @Basic
    @Type(type = "text")
    String exception
    /**
     * 解析结果
     */
    @Lob
    @Basic
    @Type(type = "text")
    String resolveResult
    /**
     * 解析异常
     */
    @Lob
    @Basic
    @Type(type = "text")
    String resolveException
}

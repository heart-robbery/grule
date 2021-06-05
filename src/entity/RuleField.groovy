package entity

import cn.xnatural.jpa.LongIdEntity
import org.hibernate.annotations.DynamicUpdate

import javax.persistence.*

@Table(indexes = [
    @Index(name = "idx_enName", columnList = "enName", unique = true),
    @Index(name = "idx_cnName", columnList = "cnName", unique = true),
])
@Entity
@DynamicUpdate
class RuleField extends LongIdEntity {
    /**
     * 英文名 唯一
     */
    @Column(nullable = false)
    String    enName
    /**
     * 中文名 唯一
     */
    @Column(nullable = false)
    String    cnName
    /**
     * 备注说明
     */
    String    comment
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    FieldType type
    /**
     * 收集器选项
     * [{collectorId: '收集器id', chooseFn: '选择函数,返回true则使用此收集器'}]
     */
    String    collectorOptions
    /**
     * 创建者
     */
    String    creator
    /**
     * 更新人
     */
    String    updater
}

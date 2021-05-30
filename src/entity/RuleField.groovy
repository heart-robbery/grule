package entity

import cn.xnatural.jpa.LongIdEntity
import org.hibernate.annotations.DynamicUpdate

import javax.persistence.*

@Table(indexes = [
    @Index(name = idx_enName_decision, columnList = "enName,decision", unique = true),
    @Index(name = idx_cnName_decision, columnList = "cnName,decision", unique = true)
])
@Entity
@DynamicUpdate
class RuleField extends LongIdEntity {
    static final String idx_enName_decision = "idx_enName_decision"
    static final String idx_cnName_decision = "idx_cnName_decision"
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
     * 关联到哪个decision#id
     * 如果为空 则是公用字段
     */
    @Column(nullable = false)
    String    decision
    /**
     * 创建者
     */
    String    creator
    /**
     * 更新人
     */
    String    updater
}

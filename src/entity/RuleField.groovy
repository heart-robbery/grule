package entity

import cn.xnatural.jpa.LongIdEntity
import org.hibernate.annotations.DynamicUpdate

import javax.persistence.*

@Table(indexes = [
    @Index(name = "idx_enName_decision", columnList = "enName,decision", unique = true),
    @Index(name = "idx_cnName_decision", columnList = "cnName,decision", unique = true)
])
@Entity
@DynamicUpdate
class RuleField extends LongIdEntity {
    /**
     * 英文名 唯一
     */
    String    enName
    /**
     * 中文名 唯一
     */
    String    cnName
    /**
     * 备注说明
     */
    String    comment
    @Enumerated(EnumType.STRING)
    FieldType type
    /**
     * 值函数名 对应 {@link DataCollector#enName}
     */
    String    dataCollector
    /**
     * 关联到哪个decision#id
     * 如果为空 则是公用字段
     */
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

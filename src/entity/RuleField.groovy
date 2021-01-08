package dao.entity

import cn.xnatural.jpa.LongIdEntity
import org.hibernate.annotations.DynamicUpdate

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated

@Entity
@DynamicUpdate
class RuleField extends LongIdEntity {
    /**
     * 英文名 唯一
     */
    @Column(unique = true)
    String    enName
    /**
     * 中文名 唯一
     */
    @Column(unique = true)
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

}

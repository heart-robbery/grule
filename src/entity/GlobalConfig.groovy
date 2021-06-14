package entity

import cn.xnatural.jpa.LongIdEntity
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.Type

import javax.persistence.*

/**
 * 全局配置 key value形式
 */
@Entity
@DynamicUpdate
@Table(indexes = [
        @Index(name = "idx_name", columnList = "name", unique = true),
])
class GlobalConfig extends LongIdEntity {
    /**
     * 属性名
     */
    @Column(nullable = false)
    String name
    /**
     * 值
     */
    @Lob
    @Basic
    @Type(type = "text")
    String value
}

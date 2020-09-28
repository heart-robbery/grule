package dao.entity

import core.jpa.LongIdEntity
import org.hibernate.annotations.DynamicUpdate

import javax.persistence.Column
import javax.persistence.Entity

/**
 * 全局配置 key value形式
 */
@Entity
@DynamicUpdate
class GlobalConfig extends LongIdEntity {
    /**
     * 配置 key
     */
    @Column(unique = true)
    String key
    /**
     * 值
     */
    String value
    /**
     * 额外属性1
     */
    String prop1
    /**
     * 额外属性2
     */
    String prop2
}

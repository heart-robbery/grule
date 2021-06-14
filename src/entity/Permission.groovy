package entity

import cn.xnatural.jpa.LongIdEntity
import org.hibernate.annotations.DynamicUpdate

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Index
import javax.persistence.Table


/**
 * 权限表
 */
@Entity
@DynamicUpdate
@Table(indexes = [
        @Index(name = "idx_enName", columnList = "enName", unique = true),
])
class Permission extends LongIdEntity {
    /**
     * 权限标识
     */
    @Column(nullable = false)
    String enName
    /**
     * 权限显示名
     */
    String cnName
    /**
     * 权限说明
     */
    @Column(length = 500)
    String comment
    /**
     * 动态创建权限时, 此字段标识
     */
    String mark
}

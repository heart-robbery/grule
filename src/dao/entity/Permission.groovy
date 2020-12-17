package dao.entity

import cn.xnatural.jpa.LongIdEntity
import org.hibernate.annotations.DynamicUpdate

import javax.persistence.Column
import javax.persistence.Entity


/**
 * 权限表
 */
@Entity
@DynamicUpdate
class Permission extends LongIdEntity {
    /**
     * 权限标识
     */
    @Column(unique = true)
    String enName
    /**
     * 权限显示名
     */
    String cnName
    /**
     * 权限说明
     */
    String comment
    /**
     * 动态创建权限时, 此字段标识
     */
    String mark
}

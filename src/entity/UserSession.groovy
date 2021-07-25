package entity

import cn.xnatural.jpa.BaseEntity
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.Type

import javax.persistence.Basic
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Lob
import javax.persistence.Table

/**
 * 用户会话记录
 */
@Entity
@DynamicUpdate
@Table(indexes = [
        @Index(name = "idx_userId", columnList = "userId"),
])
class UserSession extends BaseEntity {
    /**
     * 会话id
     */
    @Id
    @Column(length = 128)
    String sessionId
    /**
     * 关联用户id
     */
    @Column(nullable = false)
    Long userId
    /**
     * 是否有效. 主动退出 valid: false
     */
    Boolean valid
    /**
     * 当前会话属性
     */
    @Lob
    @Basic
    @Type(type = "text")
    String data
}

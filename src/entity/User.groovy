package entity

import cn.xnatural.jpa.LongIdEntity
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.Type

import javax.persistence.Basic
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Index
import javax.persistence.Lob
import javax.persistence.Table

@Entity
@DynamicUpdate
@Table(indexes = [
        @Index(name = "idx_name", columnList = "name", unique = true),
])
class User extends LongIdEntity  {
    /**
     * 用户登录名
     */
    @Column(nullable = false)
    String name
    /**
     * 用户组
     */
    @Column(name = "gp")
    String group
    /**
     * 登录的密码
     */
    String password
    /**
     * 权限标识列表, 用豆号分割
     */
    @Lob
    @Basic
    @Type(type = "text")
    String permissions
    /**
     * 上次登录时间
     */
    Date login
}

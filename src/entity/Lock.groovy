package entity

import cn.xnatural.jpa.LongIdEntity

import javax.persistence.Entity
import javax.persistence.Index
import javax.persistence.Table

@Entity
@Table(name = 'tb_lock', indexes = [@Index(name = "idx_name", columnList = "name", unique = true)])
class Lock extends LongIdEntity {
    /**
     * 锁 的 唯一标识
     */
    String name
    /**
     * 描述
     */
    String comment
}

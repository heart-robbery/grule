package dao.entity

import core.jpa.LongIdEntity
import org.hibernate.annotations.Type

import javax.persistence.Basic
import javax.persistence.Entity
import javax.persistence.Lob

/**
 * 操作历史记录
 */
@Entity
class OpHistory extends LongIdEntity {
    /**
     * 实体名
     */
    String entityName

    /**
     * 保存各个字段的 json 字符串
     */
    @Lob
    @Basic
    @Type(type = "text")
    String content
}

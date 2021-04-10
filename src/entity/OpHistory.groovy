package entity

import cn.xnatural.jpa.LongIdEntity
import org.hibernate.annotations.Type

import javax.persistence.*

/**
 * 操作历史记录
 */
@Entity
@Table(indexes = [@Index(name = "idx_createTime", columnList = "createTime")])
class OpHistory extends LongIdEntity {
    /**
     * 表名
     */
    String tbName
    /**
     * 操作员
     */
    String operator
    /**
     * 保存各个字段的 json 字符串
     */
    @Lob
    @Basic
    @Type(type = "text")
    String content
}

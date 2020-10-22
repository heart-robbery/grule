package dao.entity


import core.jpa.UUIDEntity
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.Type

import javax.persistence.Basic
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Lob

/**
 * 决策
 */
@Entity
@DynamicUpdate
class Decision extends UUIDEntity {
    /**
     * 决策id
     */
    @Column(unique = true)
    String decisionId
    /**
     * 决策名
     */
    String name
    /**
     * 描述说明
     */
    String comment
    /**
     * 完整 DSL
     */
    @Lob
    @Basic
    @Type(type = "text")
    String dsl
    /**
     * api 接口配置
     */
    String apiConfig
}

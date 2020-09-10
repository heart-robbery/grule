package dao.entity


import core.jpa.UUIDEntity
import org.hibernate.annotations.DynamicUpdate

import javax.persistence.Column
import javax.persistence.Entity

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
    @Column(length = 10000)
    String dsl
}

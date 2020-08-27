package dao.entity

import core.jpa.UUIDEntity

import javax.persistence.Entity

@Entity
class Decision extends UUIDEntity {
    /**
     * 决策id
     */
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
     * 决策内容
     */
    String body
}

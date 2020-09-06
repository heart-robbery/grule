package dao.entity

import core.jpa.UUIDEntity

import javax.persistence.CascadeType
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.OneToMany


/**
 * 决策
 */
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
     * 完整 DSL
     */
    String dsl
//    /**
//     * 关联的决策
//     */
//    @OneToMany(mappedBy = 'decision', cascade = CascadeType.ALL, fetch = FetchType.LAZY)
//    List<Policy> policies
}

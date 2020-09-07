package dao.entity

import core.jpa.UUIDEntity
import org.hibernate.annotations.DynamicUpdate

import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.OneToMany


/**
 * 决策
 */
@Entity
@DynamicUpdate
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
    @Column(length = 5000)
    String dsl
//    /**
//     * 关联的决策
//     */
//    @OneToMany(mappedBy = 'decision', cascade = CascadeType.ALL, fetch = FetchType.LAZY)
//    List<Policy> policies
}

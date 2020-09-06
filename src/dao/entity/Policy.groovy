package dao.entity

import core.jpa.UUIDEntity

import javax.persistence.*

/**
 * 策略: 规则集
 */
@Entity
class Policy extends UUIDEntity {
    @ManyToOne
    @JoinColumn(foreignKey = @ForeignKey(name = "null", value = ConstraintMode.NO_CONSTRAINT))
    Decision decision
    /**
     * 策略名
     */
    String name
    /**
     * 描述说明
     */
    String comment
    @OneToMany(mappedBy = 'policy', cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    List<Rule> rules
    /**
     * 执行顺序, 越大越先执行
     */
    Integer priority

}

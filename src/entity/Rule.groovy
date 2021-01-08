package entity

import cn.xnatural.jpa.UUIDEntity

import javax.persistence.*

/**
 * 规则
 */
@Entity
class Rule extends UUIDEntity {
    @ManyToOne
    @JoinColumn(foreignKey = @ForeignKey(name = "null", value = ConstraintMode.NO_CONSTRAINT))
    Policy policy
    /**
     * 规则名
     */
    String name
    /**
     * 规则描述
     */
    String comment
    /**
     * 操作代码
     */
    String operateBody
    /**
     * 人工审核代码
     */
    String reviewBody
    /**
     * 接受代码
     */
    String acceptBody
    /**
     * 拒绝代码
     */
    String rejectBody
    /**
     * 执行顺序, 越大越先执行
     */
    Integer priority
}

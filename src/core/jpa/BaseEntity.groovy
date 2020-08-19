package core.jpa

import org.hibernate.annotations.DynamicUpdate

import javax.persistence.MappedSuperclass

@MappedSuperclass
@DynamicUpdate
class BaseEntity implements IEntity {
    /**
     * 创建时间
     */
    Date createTime
    /**
     * 更新时间
     */
    Date updateTime
}

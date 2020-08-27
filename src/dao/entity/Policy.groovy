package dao.entity


import core.jpa.UUIDEntity

import javax.persistence.Entity

@Entity
class Policy extends UUIDEntity {
    /**
     * 策略名
     */
    String name
    /**
     * 描述说明
     */
    String comment
    /**
     * 策略内容
     */
    String body
}

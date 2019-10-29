package dao.entity


import core.module.jpa.UUIDEntity

import javax.persistence.Entity

@Entity
class Test extends UUIDEntity {
    String name
    Integer age
}
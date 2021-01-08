package entity

import cn.xnatural.jpa.UUIDEntity

import javax.persistence.Entity

@Entity
class Test extends UUIDEntity {
    String name
    Integer age
}
package dao.entity

import core.module.jpa.UUIDEntity

import javax.persistence.Entity

@Entity
class Component extends UUIDEntity {
    Boolean enabled
    String tag1
    String tag2
    String tag3
    String comment
    String htmlCode
    String javaCode
    String groovyCode
}

package dao.entity

import core.module.jpa.GUIDEntity

import javax.persistence.Entity

@Entity
class Component extends GUIDEntity {
    Boolean enabled
    String  tag1
    String  tag2
    String  tag3
    String  tag4
    String  comment
    String  htmlCode
    String  javaCode
    String  groovyCode
}

package core.module.jpa

import org.hibernate.annotations.GenericGenerator

import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.MappedSuperclass

@MappedSuperclass
class GUIDEntity extends BaseEntity {
    @Id
    @GeneratedValue(generator = "guid")
    @GenericGenerator(name = "guid", strategy = "org.hibernate.id.GUIDGenerator")
    String id
}

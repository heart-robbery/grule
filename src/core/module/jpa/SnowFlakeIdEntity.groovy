package core.module.jpa

import org.hibernate.annotations.GenericGenerator

import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.MappedSuperclass

/**
 * SnowFlake id 生成策略
 */
@MappedSuperclass
class SnowFlakeIdEntity extends BaseEntity {
    @Id
    @GeneratedValue(generator = "snowFlakeId")
    @GenericGenerator(name = "snowFlakeId", strategy = "core.module.jpa.SnowFlakeIdGenerator")
    private Long id;
}

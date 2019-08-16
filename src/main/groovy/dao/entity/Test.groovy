package dao.entity

import cn.xnatural.enet.server.dao.hibernate.LongIdEntity

import javax.persistence.Entity

@Entity
class Test extends LongIdEntity {
    String name;
    Integer age;
}
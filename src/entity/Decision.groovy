package entity

import cn.xnatural.jpa.UUIDEntity
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.Type

import javax.persistence.*

/**
 * 决策
 */
@Entity
@DynamicUpdate
@Table(indexes = [
        @Index(name = "idx_decisionId", columnList = "decisionId", unique = true),
])
class Decision extends UUIDEntity {
    /**
     * 决策id
     */
    @Column(nullable = false)
    String decisionId
    /**
     * 决策名
     */
    String name
    /**
     * 描述说明
     */
    String comment
    /**
     * 完整 DSL
     */
    @Lob
    @Basic
    @Type(type = "text")
    String dsl
    /**
     * api 接口配置
     [
     {
     "code": "decisionId", "name": "决策id", "type": "Str", "require": true, "fixValue": "decision_1"
     },
     {
     "code": "async", "name": "是否异步", "type": "Bool", "require": false, "defaultValue": false
     },
     {
     "code": "idNumber", "name": "身份证", "type": "Str", "require": true, "fixLength": 18
     },
     {
     "code": "mobileNo", "name": "手机号", "type": "Str", "require": true, "fixLength": 11
     },
     {
     "code": "name", "name": "姓名", "type": "Str", "require": true, "maxLength": 100
     }
     ]
     */
    @Lob
    @Basic
    @Type(type = "text")
    String apiConfig
    /**
     * 创建者
     */
    String creator
    /**
     * 更新人
     */
    String updater
}

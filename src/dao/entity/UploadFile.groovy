package dao.entity

import core.module.jpa.LongIdEntity
import org.hibernate.annotations.DynamicUpdate

import javax.persistence.Entity
import javax.persistence.Inheritance
import javax.persistence.InheritanceType

/**
 * 文件上传基本信息实体
 * @author xiangxb, 2018-10-07
 */
@Inheritance(strategy = InheritanceType.JOINED)
@Entity
@DynamicUpdate
class UploadFile extends LongIdEntity {
    /**
     * 系统生产的唯一文件名(可用作第3方文件服务器的文件id)
     */
    String finalName
    /**
     * 原始文件名
     */
    String originName
    /**
     * 备注
     */
    String comment
}

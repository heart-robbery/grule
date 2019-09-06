package dao.entity

import cn.xnatural.enet.server.dao.hibernate.LongIdEntity
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
     * 第3方 文件服务器 返回的 文件标识(用于从第3方去获取的凭证)
     */
    String thirdFileId;
    /**
     * 原始文件名
     */
    String originName;
    /**
     * 备注
     */
    String comment;
}

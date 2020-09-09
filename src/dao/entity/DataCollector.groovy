package dao.entity

import core.jpa.LongIdEntity
import org.hibernate.annotations.DynamicUpdate

import javax.persistence.Column
import javax.persistence.Entity

@Entity
@DynamicUpdate
class DataCollector extends LongIdEntity {
    /**
     * 英文名
     */
    @Column(unique = true)
    String enName
    /**
     * 中文名
     */
    String cnName
    /**
     * interface, script
     */
    String type
    /**
     * 备注说明
     */
    String comment

    /**
     * 接口url
     */
    String url
    /**
     * http method
     */
    String method
    /**
     * 请求body 字符串 模板
     */
    String bodyStr
    /**
     * application/json,multipart/form-data,application/x-www-form-urlencoded
     */
    String contentType
    /**
     * 值解析脚本
     * 格式为:
     *  {resultStr -> // 接口返回的字符串
     *
     *  }
     */
    String parseScript
    /**
     * 值计算函数
     */
    String computeScript
}

package entity

import cn.xnatural.jpa.LongIdEntity
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.Type

import javax.persistence.Basic
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Lob

/**
 * 数据收集器
 */
@Entity
@DynamicUpdate
class DataCollector extends LongIdEntity {
    /**
     * 是否可用
     */
    Boolean enabled
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
     * http, script, sql
     */
    String type
    /**
     * 备注说明
     */
    String comment
    /**
     * 创建者
     */
    String creator
    /**
     * 更新人
     */
    String updater
    /**
     * 超时(ms)
     */
    Integer timeout
    /**
     * 缓存key
     */
    String cacheKey
    /**
     * 缓存时间,单位分钟
     */
    Integer cacheTimeout


    /**
     * 接口url, jdbc url
     */
    String url

    // ======================== http =======================

    /**
     * http method
     */
    String method
    /**
     * 请求body 字符串 模板
     */
    @Lob
    @Basic
    @Type(type = "text")
    String bodyStr
    /**
     * application/json,multipart/form-data,application/x-www-form-urlencoded
     */
    String contentType
    /**
     * 值解析脚本
     * 格式为:
     *  {String resultStr -> // 接口返回的字符串
     *
     *  }
     */
    @Lob
    @Basic
    @Type(type = "text")
    String parseScript

    /**
     * 是否查得(数据是否成功)判断函数
     */
    @Lob
    @Basic
    @Type(type = "text")
    String dataSuccessScript


    // ======================= script ======================
    /**
     * 值计算函数
     */
    @Lob
    @Basic
    @Type(type = "text")
    String computeScript


    // ========================= sql ========================
    /**
     * groovy Sql 执行脚本
     */
    @Lob
    @Basic
    @Type(type = "text")
    String  sqlScript
    /**
     * sql 连接池的最小连接
     */
    Integer minIdle
    /**
     * sql 连接池的最大连接
     */
    Integer maxActive
}

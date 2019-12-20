package dao.entity

import core.module.jpa.BaseEntity

import javax.persistence.Entity

/**
 * http 接口配置
 */
@Entity
class HttpCfg extends BaseEntity {
    /**
     * 唯一标识: 英文名字
     * 不用uuid, 名字由配置定, 方便 添加前缀 分组 统一处理
     */
    String name
    /**
     * 唯一标识: 中文名
     */
    String cnName
//    /**
//     * [{"code", "a", "type", "Int,Double,String,Boolean", "required": true, "name":"参数a"}]
//     */
//    String paramCfg
    /**
     * 方法: get/post
     */
    String method
    /**
     * url 全路径
     * 例 url模板: ?a=${a}&b=${b}
     */
    String url
    /**
     * application/json, application/x-www-form-urlencoded, multipart/form-data
     */
    String contentType
    /**
     * 表单body: a=1&b=${b}
     * jsonBody: {a:"xxx", b:"${b}"}
     */
    String requestBody
    /**
     * 请求预处理脚本. 参数验证, 修改, 加密等. 取缓存
     */
    String preProcess
    /**
     * 请求执行脚本. 例如一些需要特殊调用sdk,添加特殊header(Authorization)
     */
    String exeProcess
    /**
     * 结果处理脚本. 入参: 接口原始返回字符串
     */
    String resultProcess
}

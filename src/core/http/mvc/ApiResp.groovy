package core.http.mvc

import groovy.transform.ToString

@ToString(includePackage = false, ignoreNulls = true, allNames = true)
class ApiResp<T> implements Serializable {
    /**
     * 请求是否成功
     * 00: 正常
     * 01: 通用错误
     */
    String code
    /**
     * 请求返回的数据
     */
    T      data
    /**
     * 当前请求返回说明
     */
    String desc
    /**
     * 返回处理流水号
     */
    String traceNo
    /**
     * 标记(调用方的入参, 原样返回)
     */
    String mark


    static <T> ApiResp<T> ok() { return new ApiResp(code: '00') }


    static <T> ApiResp ok(T data) { new ApiResp(code: '00', data: data) }


    static <T> ApiResp of(String code, String desc) { new ApiResp(code: code, desc: desc) }


    static <T> ApiResp fail(String errMsg) { new ApiResp(code: '01', desc: errMsg) }


    /**
     * 一般用法 ApiResp.ok().attr("aaa", 111).attr("bbb", 222)
     *
     * @param attrName
     * @param attrValue
     * @return
     */
    ApiResp<LinkedHashMap<String, Object>> attr(String attrName, Object attrValue) {
        if (!data) {
            data = new LinkedHashMap<String, Object>(5)
        }
        if (data && !(data instanceof Map)) {
            throw new IllegalArgumentException("data类型必须为Map类型")
        }
        ((Map) data).put(attrName, attrValue)
        this
    }


    ApiResp<LinkedHashMap<String, Object>> attrs(Map<String, Object> attrs) {
        attrs?.each {attr(it.key, it.value)}
        this
    }


    ApiResp desc(String desc) {this.desc = desc; this}
}
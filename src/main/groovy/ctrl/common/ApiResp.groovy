package ctrl.common

import groovy.transform.ToString

@ToString
class ApiResp<T> implements Serializable {
    /**
     * 请求是否成功
     */
    boolean success
    /**
     * 请求返回的数据
     */
    T data
    /**
     * 当前请求返回说明
     */
    String desc
    /**
     * 唯一请求id
     */
    String reqId


    static <T> ApiResp<T> ok() { return new ApiResp(success: true) }


    static <T> ApiResp ok(T data) { new ApiResp(success: true, data: data) }


    static ApiResp<LinkedHashMap<String, Object>> ok(String aName, Object aValue) {
        new ApiResp(success: true, data: [aName, aValue] as LinkedHashMap)
    }



    static <T> ApiResp fail(String errMsg) { new ApiResp(success: false, desc: errMsg) }


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
}

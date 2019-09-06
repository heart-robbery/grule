package ctrl.common

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature

class ApiResp<T> implements Serializable {
    private boolean success;
    private T data;
    private String errorMsg;
    private String reqId;

    static <T> ApiResp<T> ok() {
        return new ApiResp().setSuccess(true);
    }


    static <T> ApiResp ok(T data) {
        return new ApiResp().setSuccess(true).setData(data);
    }


    static ApiResp<LinkedHashMap<String, Object>> ok(String attrName, Object attrValue) {
        ApiResp<LinkedHashMap<String, Object>> ret = ok(new LinkedHashMap<>(5));
        ret.getData().put(attrName, attrValue);
        return ret;
    }


    static ApiResp fail(String errorMsg) {
        return new ApiResp().setSuccess(false).setErrorMsg(errorMsg).setReqId(UUID.randomUUID().toString());
    }


    /**
     * 一般用法 ApiResp.ok().attr("aaa", 111).attr("bbb", 222)
     *
     * @param attrName
     * @param attrValue
     * @return
     */
    ApiResp<LinkedHashMap<String, Object>> attr(String attrName, Object attrValue) {
        if (getData() == null) {
            setData((T) new LinkedHashMap<String, Object>(5));
        }
        if (getData() != null && !(getData() instanceof Map)) {
            throw new IllegalArgumentException(getClass().getSimpleName() + "的data类型必须为Map类型");
        }
        ((Map) getData()).put(attrName, attrValue);
        return (ApiResp<LinkedHashMap<String, Object>>) this;
    }


    boolean isSuccess() {
        return success;
    }


    T getData() {
        return data;
    }


    ApiResp<T> setSuccess(boolean pSuccess) {
        success = pSuccess;
        return this;
    }


    ApiResp<T> setData(T data) {
        this.data = data;
        return this;
    }


    String getErrorMsg() {
        return errorMsg;
    }


    ApiResp<T> setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
        return this;
    }


    String getReqId() {
        return reqId;
    }


    ApiResp<T> setReqId(String errorId) {
        this.reqId = errorId;
        return this;
    }


    def toJSONStr() { JSON.toJSONString(this, SerializerFeature.WriteMapNullValue) }


    @Override
    String toString() {
        return toJSONStr()
    }
}

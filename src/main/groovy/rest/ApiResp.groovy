package rest

import com.alibaba.fastjson.JSON
import groovy.transform.ToString

@ToString
class ApiResp<T> implements Serializable {
    boolean success;
    T       data;
    String  errorMsg;
    String  errorId;

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
        return new ApiResp().setSuccess(false).setErrorMsg(errorMsg);
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


    String getErrorId() {
        return errorId;
    }


    ApiResp<T> setErrorId(String errorId) {
        this.errorId = errorId;
        return this;
    }

    def toJSONStr() { JSON.toJSONString(this) }
}

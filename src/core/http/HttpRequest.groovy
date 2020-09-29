package core.http

import com.alibaba.fastjson.JSON


/**
 * http 请求 内容
 */
class HttpRequest {
    // 请求的创建时间
    final Date createTime = new Date()
    // HTTP/HTTPS
    protected String protocol
    // GET/POST
    protected String method
    // 原始url地址字符串
    protected String rowUrl
    // http协议版本: 1.0/1.1/1.2
    protected String version
    protected String bodyStr
    protected final Map<String, String> headers = new HashMap<>()
    protected final HttpDecoder decoder = new HttpDecoder(this)
    protected final HttpAioSession session


    HttpRequest(HttpAioSession session) { this.session = session }


    @Lazy String id = {
        String reqId = getHeader('X-Request-ID')
        if (reqId) return reqId
        UUID.randomUUID().toString().replace("-", "")
    }()

    @Lazy String contentType = getHeader('content-type')

    @Lazy Map<String, String> cookies = getHeader('Cookie').split(';').collectEntries {entry ->
        entry?.trim()?.split('=')
    }

    // 查询字符串
    @Lazy String queryStr = {
        int i = rowUrl.indexOf("?")
        i == -1 ? null : rowUrl.substring(i + 1)
    }()

    @Lazy Map<String, Object> queryParams = {
        if (queryStr) {
            Map<String, Object> data = new HashMap<>()
            queryStr.split("&").each {s ->
                def arr = s.split("=")
                String name = URLDecoder.decode(arr[0], 'utf-8')
                String value = arr.length > 1 ? URLDecoder.decode(arr[1], 'utf-8') : null
                if (data.containsKey(name)) { // 证明name 有多个值
                    def v = data.get(name)
                    if (v instanceof List) v.add(value)
                    else {
                        data.put(name, [v, value])
                    }
                } else {
                    data.put(name, value)
                }
            }
            return Collections.unmodifiableMap(data)
        }
        Collections.emptyMap()
    }()

    @Lazy String path = {
        int i = rowUrl.indexOf("?")
        i == -1 ? rowUrl : rowUrl.substring(0, i)
    }()

    @Lazy Map<String, Object> formParams = {
        Map<String, Object> data = new HashMap<>()
        if (bodyStr && contentType?.contains('application/x-www-form-urlencoded')) {
            bodyStr.split("&").each {s ->
                def arr = s.split("=")
                String name = URLDecoder.decode(arr[0], 'utf-8')
                String value = arr.length > 1 ? URLDecoder.decode(arr[1], 'utf-8') : null
                if (data.containsKey(name)) { // 证明name 有多个值
                    def v = data.get(name)
                    if (v instanceof List) v.add(value)
                    else {
                        data.put(name, [v, value])
                    }
                } else {
                    data.put(name, value)
                }
            }
        }
        data
    }()

    @Lazy Map<String, Object> jsonParams = {
        if (bodyStr && getHeader('content-type')?.contains('application/json')) {
            try {
                return JSON.parseObject(bodyStr)
            } catch (ex) {
                session.server.log.error("Request body is not a JSON: " + bodyStr)
            }
        }
        Collections.emptyMap()
    }()


    /**
     * 取Header值
     * @param hName
     * @return
     */
    String getHeader(String hName) {headers.get(hName.toLowerCase())}


    /**
     * 取cookie值
     * @param cName cookie 名
     * @return cookie 值
     */
    String cookie(String cName) { cookies.get(cName) }


    // 请求属性集
    String getProtocol() { return protocol }
    String getMethod() { return method }
    String getRowUrl() { return rowUrl }
    String getVersion() { return version }
    String getBodyStr() {return bodyStr}
}

package core.module.http


/**
 * http 请求 内容
 */
class HttpRequest {
    // HTTP/HTTPS
    protected String protocol
    // GET/POST
    protected String method
    // 原始url地址字符串
    protected String rowUrl
    // http协议版本: 1.0/1.1/1.2
    protected String version
    @Lazy String id = UUID.randomUUID().toString().replace("-", "")
    // 查询字符串
    @Lazy String queryStr = {
        int i = rowUrl.indexOf("?")
        i == -1 ? null : rowUrl.substring(i)
    }()
    protected final Map<String, String> headers = new HashMap<>()

    String getHeader(String hName) {headers.get(hName)}


    // 请求属性集
    String getProtocol() { return protocol }
    String getMethod() { return method }
    String getRowUrl() { return rowUrl }
    String getVersion() { return version }
}

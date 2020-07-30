package core.module.http

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature
import core.module.http.mvc.ApiResp

/**
 * http 请求 处理上下文
 */
class HttpContext {
    final HttpRequest         request
    protected final HttpAioSession      aioSession
    final HttpResponse        response
    final Map<String, Object> pathToken = new HashMap<>(7)


    HttpContext(HttpRequest request, HttpAioSession aioSession) {
        assert request
        assert aioSession
        this.request = request
        this.aioSession = aioSession
        this.response = new HttpResponse()
    }


    void close() {
        aioSession?.close()
    }


    /**
     * 响应请求
     * @param body
     */
    void render(Object body) {
        boolean f = response.commit.compareAndSet(false, true)
        if (!f) throw new Exception("已经提交过")

        if (body instanceof ApiResp) {
            body.mark = param('mark')
            body = JSON.toJSONString(body, SerializerFeature.WriteMapNullValue)
        }
        if (body instanceof String) {
            if (!response.header('content-length')) {
                response.header('content-length', body.getBytes('utf-8').length)
            }
            response.contentTypeIfNotSet('text/plan; charset=utf-8')
        } else if (body instanceof File) {
            if (body.name.endsWith(".html")) {
                response.contentTypeIfNotSet('text/html')
            }
            else if (body.name.endsWith(".css")) {
                response.contentTypeIfNotSet('text/css')
            }
            else if (body.name.endsWith(".js")) {
                response.contentTypeIfNotSet('application/javascript')
            }
            if (!response.header('content-length')) {
                response.header('content-length', body.size())
            }
        } else {
            throw new Exception("不支持的类型 " + body.getClass())
        }

        if (response.status() == null) response.status(200)
        StringBuilder sb = new StringBuilder()
        sb.append("HTTP/1.1 $response.status ${HttpResponse.statusMsg[(response.status)]}\n".toString()) // 起始行
        response.headers.each { e ->
            sb.append(e.key).append(": ").append(e.value).append("\n")
        }
        response.cookies.each { e ->
            sb.append("set-cookie=").append(e).append("\n")
        }
        sb.append('\r\n')

        if (body instanceof String) sb.append(body)
        aioSession.send(sb.toString())
    }


    /**
     * 获取参数
     * @param pName
     * @param type
     * @return
     */
    def <T> T param(String pName, Class<T> type = null) {
        def v = pathToken.get(pName)
        if (v == null) v = request.queryParams.get(pName)
        if (v == null) v = request.formParams.get(pName)
        if (v == null) v = request.jsonParams.get(pName)
        if (v == null || type == null) return v
        if (type == String) return String.valueOf(v)
        else if (type == Integer || type == int) return Integer.valueOf(v)
        else if (type == Long || type == long) return Long.valueOf(v)
        else if (type == Double || type == double) return Double.valueOf(v)
        else if (type == Float || type == float) return Float.valueOf(v)
        else if (type == BigDecimal) return new BigDecimal(v.toString())
        else if (type == URI) return URI.create(v.toString())
        else if (type == URL) return URI.create(v.toString()).toURL()
        else throw new IllegalArgumentException("不支持的类型: " + type.name)
    }
}

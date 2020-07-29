package core.module.http

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


    /**
     * 响应请求
     * @param body
     */
    void render(Object body) {
        boolean f = response.commit.compareAndSet(false, true)
        if (!f) throw new Exception("已经提交过")

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
    def <T> T param(String pName, Class<T> type) {
        def v = pathToken.get(pName)
        if (v != null) return type.cast(v)
        v = request.queryParams.get(pName)
        if (v != null) return type.cast(v)
        v = request.formParams.get(pName)
        if (v != null) return type.cast(v)
        v = request.jsonParams.get(pName)
        if (v != null) return type.cast(v)
        null
    }
}

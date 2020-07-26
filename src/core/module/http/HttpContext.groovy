package core.module.http

/**
 * http 请求 处理上下文
 */
class HttpContext {
    final HttpRequest httpRequest
    final HttpSession httpSession
    final HttpResponse resp


    HttpContext(HttpRequest httpRequest, HttpSession httpSession) {
        assert httpRequest
        assert httpSession
        this.httpRequest = httpRequest
        this.httpSession = httpSession
        this.resp = new HttpResponse()
    }


    /**
     * 响应请求
     * @param body
     */
    void render(Object body) {
        boolean f = resp.commit.compareAndSet(false, true)
        if (!f) throw new Exception("已经提交过")

        if (body == null) resp.status = 202
        else if (body instanceof String) {
            if (!resp.header('Content-length')) {
                resp.header('Content-Length', body.getBytes('utf-8').length)
            }
            if (!resp.header('Content-Type')) {
                resp.header('Content-Type', 'text/plan; charset=utf-8')
            }
        } else if (body instanceof File) {
            if (!resp.header('Content-length')) {
                resp.header('Content-Length', body.size())
            }
            if (!resp.header('Content-Type')) {
                if (body.name.endsWith(".html")) {
                    resp.header('Content-Type', 'text/html')
                }
                else if (body.name.endsWith(".css")) {
                    resp.header('Content-Type', 'text/css')
                }
            }
        } else {
            throw new Exception("不支持的类型 " + body.getClass())
        }

        StringBuilder sb = new StringBuilder()
        sb.append("HTTP/1.1 $resp.status ${HttpResponse.statusMsg[(resp.status)]}\n".toString()) // 起始行
        resp.headers.each {e ->
            sb.append(e.key).append(": ").append(e.value).append("\n")
        }
        sb.append('\r\n')

        if (body instanceof String) sb.append(body)
        httpSession.send(sb.toString())
    }
}

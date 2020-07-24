package core.module.http

/**
 * http 请求 处理上下文
 */
class HttpContext {
    final HttpRequest httpRequest
    final HttpSession httpSession

    HttpContext(HttpRequest httpRequest, HttpSession httpSession) {
        this.httpRequest = httpRequest
        this.httpSession = httpSession
    }
}

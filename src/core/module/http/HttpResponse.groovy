package core.module.http


import java.util.concurrent.atomic.AtomicBoolean

class HttpResponse {
    Integer status
    protected final Map<String, String> headers = new HashMap<>()
    protected final Map<String, String> cookies = new HashMap<>()
    final AtomicBoolean commit = new AtomicBoolean(false)
    static final Map<Integer, String> statusMsg


    static {
        statusMsg = [
            100: 'CONTINUE', 101: 'SWITCHING_PROTOCOLS', 102: 'PROCESSING', 103: 'EARLY_HINTS',
            200: 'OK', 201: 'CREATED', 202: 'ACCEPTED', 203: 'NON_AUTHORITATIVE_INFO', 204: 'NO_CONTENT', 205: 'RESET_CONTENT', 206: 'PARTIAL_CONTENT', 207: 'MULTI_STATUS', 208: 'ALREADY_REPORTED', 226: 'IM_USED',
            300: 'MULTIPLE_CHOICES', 301: 'MOVED_PERMANENTLY', 302: 'FOUND', 303: 'SEE_OTHER', 304: 'NOT_MODIFIED', 305: 'USE_PROXY', 307: 'TEMPORARY_REDIRECT', 308: 'PERMANENT_REDIRECT',
            400: 'BAD_REQUEST', 401: 'UNAUTHORIZED', 402: 'PAYMENT_REQUIRED', 403: 'FORBIDDEN', 404: 'NOT_FOUND', 405: 'METHOD_NOT_ALLOWED', 406: 'NOT_ACCEPTABLE', 407: 'PROXY_AUTH_REQUIRED', 408: 'REQUEST_TIMEOUT', 409: 'CONFLICT', 410: 'GONE', 411: 'LENGTH_REQUIRED', 412: 'PRECONDITION_FAILED', 413: 'PAYLOAD_TOO_LARGE', 414: 'URI_TOO_LONG', 415: 'UNSUPPORTED_MEDIA_TYPE', 416: 'RANGE_NOT_SATISFIABLE', 417: 'EXPECTATION_FAILED', 418: 'IM_A_TEAPOT', 421: 'MISDIRECTED_REQUEST', 422: 'UNPROCESSABLE_ENTITY', 423: 'LOCKED', 424: 'FAILED_DEPENDENCY', 426: 'UPGRADE_REQUIRED', 428: 'PRECONDITION_REQUIRED', 429: 'TOO_MANY_REQUESTS', 431: 'HEADER_FIELDS_TOO_LARGE', 451: 'UNAVAILBLE_FOR_LEGAL_REASONS',
            500: 'INTERNAL_SERVER_ERROR', 501: 'NOT_IMPLEMENTED', 502: 'BAD_GATEWAY', 503: 'SERVICE_UNAVAILABLE', 504: 'GATEWAY_TIMEOUT', 505: 'HTTP_VER_NOT_SUPPORTED', 506: 'VARIANT_ALSO_NEGOTIATES', 507: 'INSUFFICIENT_STORAGE', 508: 'LOOP_DETECTED', 510: 'NOT_EXTENDED', 511: 'NETWORK_AUTH_REQUIRED'
        ]
    }


    HttpResponse status(int status) {this.status = status; this}
    HttpResponse statusIfNotSet(int status) {if (this.status == null) this.status = status; this}


    /**
     * 设置cookie
     * @param cName cookie 名
     * @param cValue cookie 值
     * @param maxAge
     * @param domain
     * @param path
     * @param secure
     * @param httpOnly
     * @return
     */
    HttpResponse cookie(
        String cName, String cValue, Long maxAge = null, String domain = null,
        String path = null, Boolean secure = null, Boolean httpOnly = null
    ) {
        // 删除已存在的
        cookies.remove(cName)

        cookies.put(cName,
            (cValue == null ? '' : cValue)
            + (maxAge == null ? "" : "; max-age="+maxAge)
            + (domain == null ? '' : "; domain="+domain)
            + (path == null ? '' : "; path="+path)
            + (secure ? '' : "; secure")
            + (httpOnly ? '' : "; httpOnly")
        )
        this
    }


    /**
     * 让 cookie 过期
     * @param name
     */
    HttpResponse expireCookie(String name) { cookie(name, '', 0) }


    /**
     * 设置header
     * @param hName
     * @param hValue
     * @return
     */
    HttpResponse header(String hName, Object hValue) {
        headers.put(hName.toLowerCase(), hValue?.toString())
        this
    }


    String header(String hName) { return headers.get(hName.toLowerCase()) }


    HttpResponse cacheControl(Integer maxAge) {
        header('cache-control', "max-age=" + maxAge)
        this
    }


    HttpResponse contentType(CharSequence contentType) { header('content-type', contentType) }


    HttpResponse contentTypeIfNotSet(CharSequence contentType) {
        if (!headers.containsKey('content-type')) {
            header('content-type', contentType)
        }
    }


    HttpResponse contentLengthIfNotSet(int length) {
        if (!headers.containsKey('content-length')) {
            header('content-length', length)
        }
    }
}

package core.module.http

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature
import core.module.http.mvc.ApiResp
import core.module.http.mvc.Handler

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * http 请求 处理上下文
 */
class HttpContext {
    final HttpRequest         request
    protected final HttpAioSession      aioSession
    protected final HttpServer server
    final HttpResponse        response
    final Map<String, Object> pathToken = new HashMap<>(7)
    // 路径块
    protected String[] pieces
    protected final        AtomicBoolean closed      = new AtomicBoolean(false)
    /**
     * 业务 code
     */
    String respCode



    HttpContext(HttpRequest request, HttpServer server) {
        assert request : 'request must not be null'
        this.request = request
        this.aioSession = request.session
        this.server = server
        this.response = new HttpResponse()
        this.pieces = Handler.extract(request.path).split('/')
    }


    void close() {
        if (closed.compareAndSet(false, true)) {
            aioSession?.close()
        }
    }


    /**
     * 响应请求
     * @param body
     */
    void render(Object body = null) {
        boolean f = response.commit.compareAndSet(false, true)
        if (!f) throw new Exception("已经提交过")

        if (body instanceof ApiResp) {
            body.mark = param('mark')
            body.traceNo = request.id
            body = JSON.toJSONString(body, SerializerFeature.WriteMapNullValue)
            response.contentTypeIfNotSet('application/json')
        }

        fullResponse(body)
        int chunkedSize = chunkedSize(body)
        if (chunkedSize > 0) { //分块传送
            chunked(body, chunkedSize)
        } else { //整体传送
            // HttpResponseEncoder
            if (body instanceof String) {
                aioSession.send(ByteBuffer.wrap((preRespStr() + body).getBytes('utf-8')))
            } else if (body instanceof File) {
                if (body.exists()) {
                    byte[] bs = preRespStr().getBytes('utf-8')
                    def buf = ByteBuffer.allocate((int) body.length() + bs.length)
                    buf.put(bs)
                    body.withInputStream {is ->
                        do {
                            int b = is.read()
                            if (-1 == b) break
                            else buf.put((byte) b)
                        } while (true)
                    }
                    buf.flip()
                    aioSession.send(buf)
                } else {
                    aioSession.send(ByteBuffer.wrap(preRespStr().getBytes('utf-8')))
                }
            }
        }

        determineClose()
    }


    /**
     * 分块传送
     * @param body
     * @param chunkedSize
     */
    protected void chunked(Object body, int chunkedSize) {
        aioSession.send(ByteBuffer.wrap(preRespStr().getBytes('utf-8')))
        if (body instanceof String) {

        } else if (body instanceof File) {
            body.withInputStream {is ->
                ByteBuffer buf = ByteBuffer.allocate(chunkedSize)
                boolean end = false
                do {
                    int b = is.read()
                    if (-1 == b) end = true
                    else buf.put((byte) b)
                    // buf 填满 或者 结束
                    if (!buf.hasRemaining() || end) {
                        buf.flip()
                        byte[] headerBs = (Integer.toHexString(buf.limit()) + '\r\n').getBytes('utf-8')
                        byte[] endBs = '\r\n'.getBytes('utf-8')
                        def bb = ByteBuffer.allocate(headerBs.length + buf.limit() + endBs.length)
                        bb.put(headerBs); bb.put(buf); bb.put(endBs)
                        bb.flip()
                        aioSession.send(bb)
                        buf.clear()
                    }
                } while (!end)
                // 结束chunk
                aioSession.send(ByteBuffer.wrap('0\r\n\r\n'.getBytes('utf-8')))
            }
        }
    }


    /**
     * 分块传送的大小
     * @return
     */
    protected int chunkedSize(Object body) {
        int bodyLength
        if (body instanceof File) bodyLength = body.length()
        else if (body instanceof String) bodyLength = body.getBytes('utf-8').length

        int chunkedSize = -1
        if (bodyLength > 1024 * 1024 * 100) { // 大于100M
            chunkedSize = 1024 * 1024 * 5
            response.header('Transfer-Encoding', 'chunked')
        } else if (bodyLength > 1024 * 1024 * 20) { // 大于20M
            chunkedSize = 1024 * 1024 * 2
            response.header('Transfer-Encoding', 'chunked')
        } else if (bodyLength > 1024 * 200) { // 大于200K
            chunkedSize = 1024 * 100
            response.header('Transfer-Encoding', 'chunked')
        } else {
            chunkedSize = -1
            response.contentLengthIfNotSet(bodyLength)
        }
        chunkedSize
    }


    /**
     * http 响应的前半部分(起始行和header)
     * @return
     */
    protected String preRespStr() {
        StringBuilder sb = new StringBuilder()
        sb.append("HTTP/1.1 $response.status ${HttpResponse.statusMsg[(response.status)]}\r\n".toString()) // 起始行
        response.headers.each { e ->
            sb.append(e.key).append(": ").append(e.value).append("\r\n")
        }
        response.cookies.each { e ->
            sb.append("set-cookie=").append(e).append("\r\n")
        }
        sb.append('\r\n').toString()
    }


    /**
     * 填充 response
     * @param body
     */
    protected void fullResponse(Object body) {
        if (body instanceof String) {
            response.contentTypeIfNotSet('text/plain')
        } else if (body instanceof File) {
            if (body.exists()) {
                if (body.name.endsWith(".html")) {
                    response.contentTypeIfNotSet('text/html')
                }
                else if (body.name.endsWith(".css")) {
                    response.contentTypeIfNotSet('text/css')
                }
                else if (body.name.endsWith(".js")) {
                    response.contentTypeIfNotSet('application/javascript')
                }
            } else {
                response.status(404)
                response.contentLengthIfNotSet(0)
            }
        } else if (body == null) {
            response.statusIfNotSet(202)
            response.contentLengthIfNotSet(0)
        } else {
            throw new Exception("不支持的类型 " + body.class.simpleName)
        }
        response.statusIfNotSet(200)
    }


    /**
     * 判断是否应该关闭此次Http连接会话
     */
    protected void determineClose() {
        String connection = request.getHeader('connection')
        if (connection?.containsIgnoreCase('close')) {
            // http/1.1 规定 只有显示 connection:close 才关闭连接
            close()
        }
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

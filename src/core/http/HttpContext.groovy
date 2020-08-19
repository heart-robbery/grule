package core.http

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature
import core.EhcacheSrv
import core.RedisClient
import core.http.mvc.ApiResp
import core.http.mvc.FileData
import core.http.mvc.Handler

import java.nio.ByteBuffer
import java.security.AccessControlException
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * http 请求 处理上下文
 */
class HttpContext {
    final HttpRequest request
    protected final HttpAioSession aioSession
    protected final HttpServer server
    final HttpResponse response
    final Map<String, Object> pathToken = new HashMap<>(7)
    // 路径块
    protected String[] pieces
    protected final AtomicBoolean closed = new AtomicBoolean(false)
    @Lazy protected Map<String, Object> attrs = new ConcurrentHashMap<>()
    @Lazy protected def ehcache = server.bean(EhcacheSrv)
    // session id
    String sId
    // session 数据
    protected Map<String, Object> sData
    /**
     * 接口业务 code
     */
    String respCode
    /**
     * 接口业务说明
     */
    String respMsg


    HttpContext(HttpRequest request, HttpServer server) {
        assert request : 'request must not be null'
        this.request = request
        this.aioSession = request.session
        this.server = server
        this.response = new HttpResponse()
        this.pieces = Handler.extract(request.path).split('/')
    }


    /**
     * 关闭
     */
    void close() {
        if (closed.compareAndSet(false, true)) {
            aioSession?.close()
        }
    }


    /**
     * 设置请求属性
     * @param key
     * @param value
     * @return
     */
    HttpContext setAttr(String key, Object value) {
        attrs.put(key, value)
        this
    }


    /**
     * 获取请求属性
     * @param key
     * @param type
     * @return
     */
    def <T> T getAttr(String key, Class<T> type = null) {
        def v = attrs.get(key)
        if (type) return type.cast(v)
        v
    }


    /**
     * 设置session属性
     * @param key
     * @param value
     * @return
     */
    HttpContext setSessionAttr(String key, Object value) {
        getOrCreateSData()
        if (value == null) sData.remove(key)
        else sData.put(key, value instanceof Collection ? value.join(',') : value?.toString())
        this
    }


    /**
     * 获取请求属性
     * @param key
     * @param type
     * @return
     */
    def <T> T getSessionAttr(String key, Class<T> type = null) {
        getOrCreateSData()
        def v = sData.get(key)
        if (type) {
            if (Set.isAssignableFrom(type) && v instanceof String) {
                return v.toString().split(',').toList().toSet()
            } else if (List.isAssignableFrom(List) && v instanceof String) {
                return v.toString().split(',').toList()
            }
            return type.cast(v)
        }
        v
    }


    /**
     * 获取 或 创建 session 数据集
     */
    protected void getOrCreateSData() {
        if (sData != null) return
        if (sId == null) sId = request.cookie('sId')
        def expire = Duration.ofMinutes(server.getInteger('session.expire', 30))
        if ('redis' == server.getStr('session.type')) { // session的数据, 用redis 保存 session 数据
            def redis = server.bean(RedisClient)
            String cKey
            if (!sId || ((cKey = 'session:' + sId) && !redis.exists(cKey))) {
                sId = UUID.randomUUID().toString().replace('-', '')
                cKey = 'session:' + sId
                server.log.info("New session '{}'", sId)
            }
            sData = new Map<String, Object>() {
                @Override
                int size() { redis.exec {jedis -> jedis.hkeys(cKey).size()} }
                @Override
                boolean isEmpty() { redis.exec {jedis -> jedis.hkeys(cKey).isEmpty()} }
                @Override
                boolean containsKey(Object key) { redis.hexists(cKey, key?.toString()) }
                @Override
                boolean containsValue(Object value) { redis.exec {jedis-> jedis.hvals(cKey).contains(value)} }
                @Override
                Object get(Object key) { redis.hget(cKey, key?.toString()) }
                @Override
                Object put(String key, Object value) { redis.hset(cKey, key?.toString(), value?.toString(), expire.seconds.intValue()) }
                @Override
                Object remove(Object key) { redis.hdel(cKey, key?.toString()) }
                @Override
                void putAll(Map<? extends String, ?> m) { m?.each {e -> redis.hset(cKey, e.key, e.value.toString(), expire.seconds.intValue())} }
                @Override
                void clear() { redis.del(cKey) }
                @Override
                Set<String> keySet() { redis.exec {jedis-> jedis.hkeys(cKey)} }
                @Override
                Collection<Object> values() { redis.exec {jedis-> jedis.hvals(cKey)} }
                @Override
                Set<Map.Entry<String, Object>> entrySet() {
                    redis.exec {jedis -> jedis.hgetAll(cKey).entrySet()}
                }
            }
        } else { // 默认用ehcache 做session 数据管理
            String cKey
            if (!sId || ((cKey = 'session_' + sId) && ehcache.getCache(cKey) == null)) {
                sId = UUID.randomUUID().toString().replace('-', '')
                cKey = 'session_' + sId
                server.log.info("New session '{}'", sId)
            }

            def cache = ehcache.getOrCreateCache(cKey, expire,
                server.getInteger('session.maxLimit', 100000), null
            )
            cache.put('id', sId)
            sData = new Map<String, Object>() {
                @Override
                int size() { cache.size() }
                @Override
                boolean isEmpty() { size() == 0 }
                @Override
                boolean containsKey(Object key) { cache.containsKey(key) }
                @Override
                boolean containsValue(Object value) { cache.find {it.value == value} }
                @Override
                Object get(Object key) { cache.get(key) }
                @Override
                Object put(String key, Object value) { cache.put(key, value) }
                @Override
                Object remove(Object key) { cache.remove(key) }
                @Override
                void putAll(Map<? extends String, ?> m) { cache.putAll(m) }
                @Override
                void clear() { cache.clear() }
                @Override
                Set<String> keySet() { cache.iterator().collect {it.key}.toSet() }
                @Override
                Collection<Object> values() { cache.iterator().collect {it.value} }
                @Override
                Set<Map.Entry<String, Object>> entrySet() { cache.iterator().collect {it}.toSet() }
            }
        }
        response.cookie('sId', sId, expire.seconds)
    }


    /**
     * 权限验证
     * @param authority 权限名 验证用户是否有此权限
     * @return true: 验证通过
     */
    boolean auth(String authority) {
        if (!authority) throw new IllegalArgumentException('authority is empty')
        def doAuth = {Set<String> uAuthorities -> // 权限验证函数
            if (uAuthorities.contains(authority)) return true
            else throw new AccessControlException('没有权限')
        }

        Set<String> uAuthorities = getSessionAttr('uAuthorities', Set) // 当前用户的所有权限. 例: auth1,auth2,auth3
        if (uAuthorities != null) {
            def f = doAuth(uAuthorities)
            if (f) return true
        }
        else uAuthorities = ConcurrentHashMap.newKeySet()

        // 收集用户权限
        Set<String> uRoles = getSessionAttr('uRoles', Set) // 当前用户的角色. 例: role1,role2,role3
        if (uRoles == null) throw new AccessControlException('没有权限')
        uRoles.each {role ->
            server.attr('role')[(role)].each {String auth -> uAuthorities.add(auth)}
        }
        setSessionAttr('uAuthorities', uAuthorities)
        doAuth(uAuthorities)
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
        if (chunkedSize > 0 && body !instanceof String) { //分块传送
            chunked(body, chunkedSize)
        } else { //整体传送
            // HttpResponseEncoder
            if (body == null) {
                aioSession.send(ByteBuffer.wrap(preRespStr().getBytes('utf-8')))
            } else if (body instanceof String) {
                aioSession.send(ByteBuffer.wrap((preRespStr() + body).getBytes('utf-8')))
            } else if (body instanceof byte[]) {
                aioSession.send(ByteBuffer.wrap(body))
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
        else if (body instanceof byte[]) {
            bodyLength = body.length
            return -1
        }
        else if (body instanceof String) bodyLength = body.getBytes('utf-8').length

        // 下载限速
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
            sb.append("Set-Cookie: ").append(e.key).append('=').append(e.value).append("\r\n")
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
            response.statusIfNotSet(404)
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
     * 所有参数: 路径参数, query参数, 表单, json
     * @return
     */
    Map params() {
        Map params = new HashMap()
        params.putAll(request.jsonParams)
        params.putAll(request.formParams)
        params.putAll(request.queryParams)
        params.putAll(pathToken)
        params
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
        if (type == String) return v instanceof List ? (v?[0]) : String.valueOf(v)
        else if (type == Integer || type == int) return Integer.valueOf(v)
        else if (type == Long || type == long) return Long.valueOf(v)
        else if (type == Double || type == double) return Double.valueOf(v)
        else if (type == Float || type == float) return Float.valueOf(v)
        else if (type == BigDecimal) return new BigDecimal(v.toString())
        else if (type == FileData) return v instanceof List ? (v?[0]) : v
        else if (type == URI) return URI.create(v.toString())
        else if (type == URL) return URI.create(v.toString()).toURL()
        else if (type == List) return v instanceof List ? v : [v]
        else throw new IllegalArgumentException("不支持的类型: " + type.name)
    }
}

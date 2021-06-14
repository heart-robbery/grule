package core

import cn.xnatural.app.ServerTpl
import cn.xnatural.enet.event.EL
import cn.xnatural.http.HttpContext
import cn.xnatural.http.HttpServer

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * web 服务
 */
class HttpSrv extends ServerTpl {
    @Lazy protected def         cacheSrv          = bean(CacheSrv)
    @Lazy protected String      sessionCookieName = getStr("sessionCookieName", "sId")
    protected final List<Class> ctrlClzs          = new LinkedList<>()
    @Lazy protected HttpServer  server            = new HttpServer(attrs(), exec()) {
        @Override
        protected Map<String, Object> sessionDelegate(HttpContext hCtx) { getSessionDelegate(hCtx) }
    }


    HttpSrv(String name) { super(name) }

    HttpSrv() {super('web')}


    @EL(name = 'sys.starting', async = true)
    void start() {
        ctrlClzs.each {server.ctrls(it)}
        server.start()
        server.enabled = false
        ep.fire("${name}.started")
    }


    @EL(name = 'sys.started', async = true)
    protected void started() {
        server.ctrls.each {exposeBean(it)}
        server.enabled = true
    }


    @EL(name = 'sys.stopping', async = true)
    void stop() { server.stop() }


    /**
     * 提供session 数据操作
     * @param hCtx
     */
    protected Map<String, Object> getSessionDelegate(HttpContext hCtx) {
        Map<String, Object> sData
        String sId = hCtx.request.getCookie(sessionCookieName)
        def expire = Duration.ofMinutes(getInteger('session.expire', 30))
        if ('redis' == getStr('session.type', null)) { // session的数据, 用redis 保存 session 数据
            def redis = bean(RedisClient)
            String cKey
            if (!sId || ((cKey = 'session:' + sId) && !redis.exists(cKey))) {
                sId = UUID.randomUUID().toString().replace('-', '')
                cKey = 'session:' + sId
                log.info("New session '{}'", sId)
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
                Object put(String key, Object value) { redis.hset(cKey, key?.toString(), value instanceof Collection ? value.join(',') : value?.toString(), expire.seconds.intValue()) }
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
        } else { // 默认用内存缓存做session 数据管理
            String cKey
            if (!sId || ((cKey = 'session_' + sId) && (sData = cacheSrv.get(cKey)) == null)) {
                sId = UUID.randomUUID().toString().replace('-', '')
                cKey = 'session_' + sId
                log.info("New session '{}'", sId)
            }

            if (sData == null) {
                sData = new ConcurrentHashMap<>()
                cacheSrv.set(cKey, sData, expire)
            } else {
                cacheSrv.expire(cKey, expire)
            }
        }
        sData.put("id", sId)
        hCtx.response.cookie(sessionCookieName, sId, expire.seconds as Integer, null, "/", false, false)
        return sData
    }


    /**
     * 添加
     * @param clzs
     */
    HttpSrv ctrls(Class...clzs) { clzs?.each {ctrlClzs.add(it)}; this }


    @EL(name = "sys.heartbeat", async = true)
    protected void clean() { server.clean() }


    @EL(name = ['http.hp', 'web.hp'], async = false)
    String getHp() { server.hp }
}

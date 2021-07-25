package core

import cn.xnatural.app.CacheSrv
import cn.xnatural.app.ServerTpl
import cn.xnatural.enet.event.EL
import cn.xnatural.http.HttpContext
import cn.xnatural.http.HttpServer
import cn.xnatural.jpa.Repo
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import entity.UserSession

import java.time.Duration

/**
 * web 服务
 */
class HttpSrv extends ServerTpl {
    @Lazy protected cacheSrv = bean(CacheSrv)
    @Lazy protected repo = bean(Repo, 'jpa_rule_repo')
    @Lazy protected sessionCookieName = getStr("sessionCookieName", "sessionId")
    @Lazy protected expire = Duration.ofMinutes(getInteger('session.expire', 30))
    @Lazy protected continuousExpire = Duration.ofMinutes(getInteger('session.continuousExpire', 60 * 24 * 5))
    protected final ctrlClzs = new LinkedList<Class>()
    @Lazy protected server = new HttpServer(attrs(), exec()) {
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


    @EL(name = 'sys.stopping')
    void stop() { server.stop() }


    /**
     * 提供session 数据操作
     */
    protected Map<String, Object> getSessionDelegate(HttpContext hCtx) {
        Map<String, Object> sData
        String sId = hCtx.request.getCookie(sessionCookieName)
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
        } else { // 默认用数据库做session 数据管理
            return dbSessionDelegate(hCtx)
//            String cKey
//            if (!sId || ((cKey = 'session_' + sId) && (sData = cacheSrv.get(cKey)) == null)) {
//                sId = UUID.randomUUID().toString().replace('-', '')
//                cKey = 'session_' + sId
//                log.info("New session '{}'", sId)
//            }
//
//            if (sData == null) {
//                sData = new ConcurrentHashMap<>()
//                cacheSrv.set(cKey, sData, expire)
//            } else {
//                cacheSrv.expire(cKey, expire)
//            }
        }
        sData.put("id", sId)
        hCtx.response.cookie(sessionCookieName, sId, expire.seconds as Integer, null, "/", false, false)
        return sData
    }


    /**
     * 数据库session
     */
    protected Map<String, Object> dbSessionDelegate(HttpContext hCtx) {
        Map<String, Object> sData
        String sessionId = hCtx.request.getCookie(sessionCookieName)
        UserSession session
        if (sessionId) {
            session = repo.findById(UserSession, sessionId)
            if (session) {
                // 一段时间不操作过期
                if (System.currentTimeMillis() - session.updateTime.time > expire.toMillis()) {
                    session.valid = false
                }
                // 如果一直操作, 则等超出continuousExpire时间后的下一天过期(在下一天第一次重新登录)
                else if (
                    System.currentTimeMillis() - session.createTime.time > continuousExpire.toMillis() &&
                    (int) (System.currentTimeMillis() / (1000 * 60 * 60 * 24)) > (int) (session.updateTime / (1000 * 60 * 60 * 24))
                ) {
                    session.valid = false
                }
                else {
                    session.updateTime = new Date()
                    sData = session.data ? JSON.parseObject(session.data) : new JSONObject()
                }
                hCtx.regFinishedFn { session.data = sData.toString(); repo.saveOrUpdate(session) }
            }
        }
        sessionId = session && session.valid ? session.sessionId : UUID.randomUUID().toString().replace('-', '')
        sData = sData == null ? new JSONObject() : sData
        sData.put("id", sessionId)
        hCtx.response.cookie(sessionCookieName, sessionId, expire.seconds as Integer, null, "/", false, false)
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

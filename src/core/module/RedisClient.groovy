package core.module

import cn.xnatural.enet.event.EL
import cn.xnatural.enet.event.EP
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.Protocol

import java.time.Duration
import java.util.function.Function

class RedisClient extends ServerTpl {
    protected JedisPool pool;

    RedisClient() { super('redis') }
    RedisClient(String name) { super(name) }


    @EL(name = 'sys.starting')
    def start() {
        if (pool) throw new RuntimeException("$name is already running")
        if (ep == null) {ep = new EP(); ep.addListenerSource(this)}

        // 连接池配置
        JedisPoolConfig poolCfg = new JedisPoolConfig(
            minIdle: Integer.valueOf(attrs.minIdle?:1), maxIdle: Integer.valueOf(attrs.maxIdle?:5),
            maxTotal: Integer.valueOf(attrs.maxTotal?:7), maxWaitMillis: Integer.valueOf(attrs.maxWaitMillis?:5000)
        )
        pool = new JedisPool(
            poolCfg, attrs.host?:'localhost', Integer.valueOf(attrs.port?:6379),
            Integer.valueOf(attrs.connectionTimeout?:3000),
            Integer.valueOf(attrs.soTimeout?:7000),
            attrs.password?:null,
            Integer.valueOf(attrs.database?:Protocol.DEFAULT_DATABASE),
            attrs.clientName?:null
        )

        exposeBean(pool)
        ep.fire("${name}.started")
        log.info("Created '{}' Client", name)
    }


    @EL(name = "sys.stopping")
    def stop() {
        log.info("Close '{}' Client", name)
        pool?.close(); pool = null
    }


    @EL(name = '${name}.hset')
    def hset(String cName, String key, Object value, Integer seconds) {
        log.trace(name + ".hset. cName: "+ cName +", key: {}, value: {}, seconds: " + seconds, key, value)
        exec{c ->
            c.hset(cName, key, value.toString())
            c.expire(cName, seconds == null ? getExpire(cName).seconds.intValue() : seconds)
        }
    }


    @EL(name = '${name}.hget', async = false)
    Object hget(String cName, String key) { exec{it.hget(cName, key)} }


    @EL(name = '${name}.hexists', async = false)
    boolean hexists(String cName, String key) { exec{it.hexists(cName, key)} }


    @EL(name = '${name}.exists', async = false)
    boolean exists(String key) { exec{it.exists(key)} }


    @EL(name = '${name}.hdel', async = false)
    def hdel(String cName, String key) {
        log.debug(name + ".hdel. cName: {}, key: {}", cName, key)
        exec{it.hdel(cName, key)}
    }


    @EL(name = '${name}.del')
    def del(String cName) {
        log.info("{}.del. cName: {}", name, cName)
        exec{it.del(cName)}
    }


    @EL(name = '${name}.exec', async = false)
    Object exec(Function<Jedis, Object> fn) {
        try (Jedis c = pool.getResource()) {fn.apply(c)}
    }


    protected Duration getExpire(String cName) {
        if (attrs.expire?.(cName) instanceof Duration) return attrs.expire?.(cName)
        else if (attrs.expire?.(cName) instanceof Number || attrs.expire?.(cName) instanceof String) return Duration.ofMinutes(Long.valueOf(attrs.expire?.(cName)))

        if (attrs.defaultExpire instanceof Duration) return attrs.defaultExpire
        else if (attrs.defaultExpire instanceof Number || attrs.defaultExpire instanceof String) return Duration.ofMinutes(Long.valueOf(attrs.defaultExpire))

        Duration.ofHours(12) // 默认12小时过期
    }
}

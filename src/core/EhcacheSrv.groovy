package core


import cn.xnatural.enet.event.EL
import cn.xnatural.enet.event.EP
import org.ehcache.Cache
import org.ehcache.CacheManager
import org.ehcache.config.builders.CacheManagerBuilder
import org.ehcache.config.builders.ExpiryPolicyBuilder
import org.ehcache.config.builders.ResourcePoolsBuilder

import java.time.Duration

import static org.ehcache.config.builders.CacheConfigurationBuilder.newCacheConfigurationBuilder
import static org.ehcache.config.builders.ResourcePoolsBuilder.newResourcePoolsBuilder
import static org.ehcache.config.units.EntryUnit.ENTRIES
import static org.ehcache.config.units.MemoryUnit.MB

class EhcacheSrv extends ServerTpl {
    static final F_NAME = 'ehcache'
    protected CacheManager cm

    EhcacheSrv() { super(F_NAME) }

    @EL(name = 'sys.starting', async = true)
    def start() {
        if (cm) throw new RuntimeException("$name is already running")
        if (ep == null) {ep = new EP(); ep.addListenerSource(this)}
        cm = CacheManagerBuilder.newCacheManagerBuilder().build(true)
        exposeBean(cm)
        log.info("Started '{}' Server", name)
        ep.fire("${name}.started")
    }


    @EL(name = 'sys.stopping')
    def stop() {
        log.debug("Close '{}'", name)
        cm?.close(); cm == null
    }


    @EL(name = '${name}.create', async = false)
    Cache<Object, Object> getOrCreateCache(final String cName, Duration expire, Integer heapOfEntries, Integer heapOfMB) {
        Cache<Object, Object> cache = cm.getCache(cName, Object.class, Object.class)
        if (cache == null) {
            synchronized (this) {
                cache = cm.getCache(cName, Object.class, Object.class) // 不同线程同时进来, cache为null
                if (cache == null) {
                    if (heapOfEntries != null && heapOfMB != null) throw new IllegalArgumentException("heapOfEntries 和 heapOfMB 不能同时设置")
                    if (heapOfEntries == null && heapOfMB == null) {heapOfEntries = Integer.valueOf(attrs().heapOfEntries?.(cName)?:getInteger("defaultHeapOfEntries", 10000))}
                    if (expire == null) expire = getExpire(cName)
                    ResourcePoolsBuilder b = newResourcePoolsBuilder()
                    if (heapOfEntries != null) b = b.heap(heapOfEntries, ENTRIES)
                    if (heapOfMB != null) b = b.heap(heapOfMB, MB)
                    log.info(name + ".create. cName: " + cName +  ", expire: " + expire + ", heapOfEntries: "+ heapOfEntries + ", heapOfMB: " + heapOfMB)
                    cache = cm.createCache(cName, newCacheConfigurationBuilder(Object.class, Object.class, b.build())
                        .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(expire))
                    )
                }
            }
        }
        cache
    }


    Cache<Object, Object> getCache(final String cName) {
        cm.getCache(cName, Object.class, Object.class)
    }


    @EL(name = ['${name}.set', "cache.set"], async = false)
    def set(String cName, Object key, Object value) {
        log.trace(name + ".set. cName: {}, key: {}, value: " + value, cName, key)
        getOrCreateCache(cName, null, null, null).put(key, value)
    }


    @EL(name = ['${name}.get', "cache.get"], async = false)
    Object get(String cName, Object key) {
        getCache(cName)?.get(key)
    }


    @EL(name = ['${name}.evict', "cache.evict"], async = false)
    def evict(String cName, Object key) {
        log.debug(name + ".evict. cName: {}, key: {}", cName, key)
        getCache(cName)?.remove(key)
    }


    @EL(name = ['${name}.clear', "cache.clear"], async = false)
    def clear(String cName) {
        log.info("{}.clear. cName: {}", name, cName)
        getCache(cName)?.clear()
    }


    /**
     * 获取 缓存 的过期时间
     * @param cName 缓存名
     * @return
     */
    protected Duration getExpire(String cName) {
        if (attrs().expire?.(cName) instanceof Duration) return attrs().expire?.(cName)
        else if (attrs().expire?.(cName) instanceof Number || attrs().expire?.(cName) instanceof String) return Duration.ofMinutes(Long.valueOf(attrs().expire?.(cName)))

        if (attrs().defaultExpire instanceof Duration) return attrs().defaultExpire
        else if (attrs().defaultExpire instanceof Number || attrs().defaultExpire instanceof String) return Duration.ofMinutes(Long.valueOf(attrs().defaultExpire))

        Duration.ofHours(12) // 默认12小时过期
    }
}

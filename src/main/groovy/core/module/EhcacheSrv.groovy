package core.module


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

    @EL(name = "sys.starting")
    def start() {
        if (cm) throw new IllegalArgumentException('Cache exist! must after stop')
        if (ep == null) {ep = new EP(); ep.addListenerSource(this)}
        cm = CacheManagerBuilder.newCacheManagerBuilder().build(true)
        exposeBean(cm)
        log.info("Started '{}' Server", name)
        ep.fire("${name}.started")
    }


    @EL(name = "sys.stopping")
    def stop() {
        log.debug("Close '{}'", name)
        cm?.close(); cm == null
    }


    @EL(name = '${name}.create', async = false)
    Cache<Object, Object> getOrCreateCache(String cName, Duration expire, Integer heapOfEntries, Integer heapOfMB) {
        Cache<Object, Object> cache = cm.getCache(cName, Object.class, Object.class)
        if (cache == null) {
            synchronized (this) {
                cache = cm.getCache(cName, Object.class, Object.class) // 不同线程同时进来, cache为null
                if (cache == null) {
                    log.info(name + ".create. cName: " + cName +  ", expire: " + expire + ", heapOfEntries: "+ heapOfEntries + ", heapOfMB: " + heapOfMB)
                    ResourcePoolsBuilder b = newResourcePoolsBuilder()
                    if (heapOfEntries != null && heapOfMB != null) throw new IllegalArgumentException("heapOfEntries 和 heapOfMB 不能同时设置")
                    else if (heapOfEntries == null && heapOfMB == null) throw new IllegalArgumentException("heapOfEntries 和 heapOfMB 必须指定一个")
                    else if (heapOfEntries != null) b = b.heap(heapOfEntries, ENTRIES)
                    else if (heapOfMB != null) b = b.heap(heapOfMB, MB)
                    cache = cm.createCache(cName, newCacheConfigurationBuilder(Object.class, Object.class, b.build())
                        .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(
                            expire?:attrs.expire.(cName)?:attrs.defaultExpire?:Duration.ofMinutes(30L)
                        ))
                    )
                }
            }
        }
        cache
    }


    @EL(name = ['${name}.set', "cache.set"], async = false)
    def set(String cName, Object key, Object value) {
        log.trace(name + ".set. cName: {}, key: {}, value: " + value, cName, key)
        getOrCreateCache(cName, null, attrs.heapOfEntries.(cName)?:attrs.defaultHeapOfEntries?:1000, null).put(key, value)
    }


    @EL(name = ['${name}.get', "cache.get"], async = false)
    Object get(String cName, Object key) {
        cm.getCache(cName, Object.class, Object.class)?.get(key)
    }


    @EL(name = ['${name}.evict', "cache.evict"], async = false)
    def evict(String cName, Object key) {
        log.debug(name + ".evict. cName: {}, key: {}", cName, key)
        cm.getCache(cName, Object.class, Object.class)?.remove(key)
    }


    @EL(name = ['${name}.clear', "cache.clear"], async = false)
    def clear(String cName) {
        log.info("{}.clear. cName: {}", name, cName)
        cm.getCache(cName, Object.class, Object.class)?.clear()
    }
}
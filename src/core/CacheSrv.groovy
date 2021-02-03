package core

import cn.xnatural.app.ServerTpl

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * 简单缓存服务
 */
class CacheSrv extends ServerTpl {

    /**
     * 数据存放
     */
    final Map<String, Record> data = new ConcurrentHashMap<>()


    /**
     * 设置缓存
     * @param key 缓存key
     * @param value 缓存值
     * @param expire 过期时间
     * @return
     */
    CacheSrv set(String key, Object value, Duration expire = Duration.ofMinutes(getInteger("defaultExpire", getInteger("expire." + key, 30)))) {
        data.put(key, new Record(value: value, expire: expire))
        if (data.size() > getInteger("itemLimit", 1000)) {
            Map.Entry<String, Record> oldEntry
            boolean del = false
            for (def itt = data.entrySet().iterator(); itt.hasNext(); ) {
                def entry = itt.next()
                if (oldEntry == null || oldEntry.value.updateTime < entry.value.updateTime) oldEntry = entry
                if (!entry.value.valid()) {
                    itt.remove()
                    del = true
                    break
                }
            }
            if (!del && oldEntry) {
                data.remove(oldEntry.key)
            }
        }
        return this
    }


    /**
     * 重新更新过期时间
     * @param key 缓存key
     * @param expire 过期时间
     * @return
     */
    CacheSrv expire(String key, Duration expire) {
        def record = data.get(key)
        if (record) {
            record.expire = expire
            record.updateTime = System.currentTimeMillis()
        }
        this
    }


    /**
     * 获取缓存值
     * @param key 缓存key
     * @return 缓存值
     */
    Object get(String key) {
        def record = data.get(key)
        if (record == null) return null
        if (record.valid()) return record.value
        data.remove(key)
        return null
    }


    protected class Record {
        Duration expire
        Long     updateTime = System.currentTimeMillis()
        Object   value

        /**
         * 是否有效
         * @return
         */
        boolean valid() {
            updateTime + expire.toMillis() > System.currentTimeMillis()
        }
    }
}

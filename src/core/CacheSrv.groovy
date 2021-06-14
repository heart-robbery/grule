package core

import cn.xnatural.app.ServerTpl

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * 简单缓存服务
 */
class CacheSrv extends ServerTpl {
    @Lazy Integer limit = getInteger("itemLimit", 1000)

    /**
     * 数据存放
     */
    @Lazy protected Map<String, Record> data = new ConcurrentHashMap( limit + 5) {
        @Override
        Object remove(Object key) {
            def v = super.remove(key)
            CacheSrv.this.log.debug("Removed cache: {}", key)
            return v
        }
    }


    /**
     * 设置缓存
     * @param key 缓存key
     * @param value 缓存值
     * @param expire 过期时间
     */
    CacheSrv set(String key, Object value, Duration expire = Duration.ofMinutes(getInteger("defaultExpire", getInteger("expire." + key, 30)))) {
        log.trace("Set cache. key: " + key + ", value: " + value + ", expire: " + expire)
        data.put(key, new Record(value: value, expire: expire))
        if (data.size() < limit) return this
        async { // 异步清除多余的缓存
            Map.Entry<String, Record> oldEntry
            long oldLeft
            for (def iter = data.entrySet().iterator(); iter.hasNext(); ) { //遍历选出最应该被移出的缓存记录
                Map.Entry<String, Record> entry = iter.next()
                long left = entry.value.left()
                if (left < 1) { // 当前缓存记录已经失效了,删除
                    iter.remove(); oldEntry = null; break
                }
                // 优先清理过期时间越近的
                if (
                    oldEntry == null ||
                    left < oldLeft ||
                    (oldLeft == left && oldEntry.value.updateTime < entry.value.updateTime)
                ) {
                    oldEntry = entry
                    oldLeft = left
                }
            }
            if (oldEntry) {
                data.remove(oldEntry.key)
            }
        }
        return this
    }


    /**
     * 重新更新过期时间
     * @param key 缓存key
     * @param expire 过期时间
     */
    CacheSrv expire(String key, Duration expire = null) {
        Record record = data.get(key)
        if (record) {
            if (expire != null) {
                record.expire = expire
                record.updateTime = System.currentTimeMillis()
                log.debug("Updated cache: {}, expire: {}", key, expire)
            } else {
                log.debug("Removed cache: {}", key)
                data.remove(key)
            }
        }
        this
    }


    /**
     * 获取缓存值
     * @param key 缓存key
     * @return 缓存值
     */
    Object get(String key) {
        Record record = data.get(key)
        if (record == null) return null
        if (record.valid()) return record.value
        data.remove(key)
        return null
    }


    /**
     * 缓存记录
     */
    protected class Record {
        Duration expire
        Long     updateTime = System.currentTimeMillis()
        Long     accessTime
        Object   value

        /**
         * 是否有效
         * @return
         */
        boolean valid() { left() }

        /**
         * 剩多长时间才失效
         */
        long left() { (updateTime + expire.toMillis()) - System.currentTimeMillis() }
    }
}

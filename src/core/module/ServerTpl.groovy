package core.module


import cn.xnatural.enet.event.EC
import cn.xnatural.enet.event.EL
import cn.xnatural.enet.event.EP
import core.AppContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.annotation.Resource
import java.util.concurrent.ExecutorService

class ServerTpl {
    protected final Logger log  = LoggerFactory.getLogger(getClass())
    /**
     * 服务名字标识.(保证唯一)
     * 可用于命名空间:
     * 1. 可用于属性配置前缀
     * 2. 可用于事件名字前缀
     */
    final           String name
    /**
     * 1. 当此服务被加入核心时, 此值会自动设置为核心的EP.
     * 2. 如果要服务独立运行时, 请手动设置
     */
    @Resource EP           ep
    @Lazy def              app  = bean(AppContext)
    @Lazy protected def    exec = bean(ExecutorService)


    ServerTpl(String name) {
        if (!name) throw new IllegalArgumentException('name must not allow empty')
        this.name = name
    }
    ServerTpl() {
        this.name = getClass().getSimpleName().uncapitalize()
    }


    /**
     * bean 容器. {@link #localBean}
     */
    protected Map<Object, Object> beanCtx
    @EL(name = ["bean.get", '${name}.bean.get'], async = false)
    protected <T> T localBean(EC ec, Class<T> bType, String bName) {
        //  已经找到结果了, 就直接返回
        if (!beanCtx || ec?.result != null) return ec?.result

        Object bean = null
        if (bName && bType) {
            bean = beanCtx[bName]
            if (bean != null && !bType.isAssignableFrom(bean.getClass())) bean = null
        } else if (bName && !bType) {
            bean = beanCtx[bName]
        } else if (!bName && bType) {
            if (bType.isAssignableFrom(getClass())) bean = this
            else {
                for (def it = beanCtx.entrySet().iterator(); it.hasNext(); ) {
                    def e = it.next()
                    if (bType.isAssignableFrom(e.value.getClass())) {
                        bean = e.value; break
                    }
                }
            }
        }
        bean
    }


    /**
     * 本地查找 对象
     * @param bType 对象类型
     * @param bName 对象名字
     * @return
     */
    protected <T> T localBean(Class<T> bType, String bName = null) { localBean(null, bType, bName) }


    /**
     * 全局查找 对象
     * @param type 对象类型
     * @param name 对象名字
     * @return
     */
    protected <T> T bean(Class<T> type, String name = null) { (T) ep.fire("bean.get", type, name) }


    /**
     * 异步执行. 拦截异常
     * @param fn
     * @return
     */
    def async(Runnable fn) {
        exec.execute {
            try {fn.run()} catch(Throwable ex) {
                log.error("Async Error", ex)
            }
        }
        fn
    }


    /**
     * 对列执行
     * @param qName 要加入的对列名, 默认当前server名称
     * @param fn 要执行的函数
     * @return {@link core.Devourer}当前对列
     */
    def queue(String qName = name, Runnable fn = null) {app.queue(qName, fn)}


    /**
     * 暴露 bean 给其它模块用. {@link #localBean}
     * @param names bean 名字.
     * @param bean
     */
    protected ServerTpl exposeBean(Object bean, List<String> names = [bean.class.simpleName.uncapitalize()]) {
        if (beanCtx == null) beanCtx = new HashMap<>()
        for (String n : names) {
            if (beanCtx[n]) log.warn("override exist bean name '{}'", n)
            beanCtx[n] = bean
        }
        this
    }


    /**
     * 属性集
     * @return
     */
    Map attrs() { app.env[(name)] }

    // 设置属性
    ServerTpl attr(String aName, Object aValue) {
        attrs().put(aName, aValue)
        this
    }

    Long getLong(String key, Long defaultValue = null) { attrs().containsKey(key) ? Long.valueOf(attrs()[(key)]) : defaultValue}

    Integer getInteger(String key, Integer defaultValue = null) { attrs().containsKey(key) ? Integer.valueOf(attrs()[(key)]) : defaultValue }

    Double getDouble(String key, Double defaultValue = null) { attrs().containsKey(key)? Double.valueOf(attrs()[(key)]) : defaultValue}

    Float getFloat(String key, Float defaultValue = null) { attrs().containsKey(key)? Float.valueOf(attrs()[(key)]) : defaultValue}

    String getStr(String key, String defaultValue = null) { attrs().containsKey(key) ? attrs()[(key)] : defaultValue}

    Boolean getBoolean(String key, Boolean defaultValue = null) { attrs().containsKey(key) ? Boolean.valueOf(attrs()[(key)]) : defaultValue}
}
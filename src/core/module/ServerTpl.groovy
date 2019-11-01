package core.module


import cn.xnatural.enet.event.EC
import cn.xnatural.enet.event.EL
import cn.xnatural.enet.event.EP
import core.AppContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.annotation.Resource

class ServerTpl {
    protected final Logger       log = LoggerFactory.getLogger(getClass())
    /**
     * 服务名字标识.(保证唯一)
     * 可用于命名空间:
     * 1. 可用于属性配置前缀
     * 2. 可用于事件名字前缀
     */
    final           String       name
    /**
     * 可配置属性集.
     */
    protected final ConfigObject attrs
    /**
     * 1. 当此服务被加入核心时, 此值会自动设置为核心的EP.
     * 2. 如果要服务独立运行时, 请手动设置
     */
    @Resource
    protected       EP           ep


    ServerTpl(String name) {
        if (!name) throw new IllegalArgumentException('name must not allow empty')
        this.name = name
        attrs = AppContext.env.(name)
    }
    ServerTpl() {
        this.name = getClass().getSimpleName().uncapitalize()
        attrs = AppContext.env.(name)
    }


    // 设置属性
    ServerTpl attr(String aName, Object aValue) {
        attrs?.put(aName, aValue)
        this
    }


    /**
     * bean 容器. {@link #findLocalBean}
     */
    protected Map<Object, Object> beanCtx
    @EL(name = ["bean.get", '${name}.bean.get'], async = false)
    protected def findLocalBean(EC ec, Class beanType, String beanName) {
        //  已经找到结果了, 就直接返回
        if (!beanCtx || ec.result) return ec.result

        Object bean = null
        if (!beanName && !beanType) {
            bean = beanCtx[beanName]
            if (bean != null && !beanType.isAssignableFrom(bean.getClass())) bean = null
        } else if (beanName && !beanType) {
            bean = beanCtx[beanName]
        } else if (!beanName && beanType) {
            if (beanType.isAssignableFrom(getClass())) bean = this
            else {
                for (def it = beanCtx.entrySet().iterator(); it.hasNext(); ) {
                    def e = it.next()
                    if (beanType.isAssignableFrom(e.value.getClass())) {
                        bean = e.value; break
                    }
                }
            }
        }
        bean
    }


    /**
     * 全局查找Bean
     * @param type
     * @param <T>
     * @return
     */
    protected <T> T bean(Class<T> type) { (T) ep.fire("bean.get", type) }



    /**
     * 暴露 bean 给其它模块用. {@link #findLocalBean}
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
}

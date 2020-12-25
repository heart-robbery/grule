package core.mode.builder
/**
 * JavaBean对象构建器, 从一个运行上下文中, 构建一个结果对象.
 * 一般用于: rest 接口, 即service 方法返回一个对象, 对象的一些属性需要复杂计算的结果
 *
 * @param <T>
 * @author hubert
 */
class ObjBuilder<T> extends Builder<T> {
    /**
     * 一个javaBean.
     */
    private       Class<T>                targetClz
    /**
     * 属性的计算是可以依赖顺序的(先计算某个属性, 再根据这个属性的值, 计算另一个属性的值)
     * NOTE: 尽量每个属性的计算不会相互依赖
     */
    private final Map<String, Builder<?>> fieldGenerators = new LinkedHashMap<>()


    static final <T> ObjBuilder<T> of(Class<T> javaBeanClz) {
        return new ObjBuilder<T>().setJavaBeanClz(javaBeanClz)
    }


    @Override
    protected boolean isValid(Map ctx) {
        if (!super.isValid(ctx)) return false
        if (getJavaBeanClz() == null) {
            log.error("property javaBeanClz must not be null")
            return false
        }
        if (!fieldGenerators) log.warn("fieldGenerators is empty!")
        return true
    }


    @Override
    protected T doBuild(Map ctx) {
        T retObj = instance(ctx)
        if (retObj == null) return null
        fieldGenerators.each{propName, builder ->
            if (builder == null) {
                log.warn("属性 " + propName + " 对应的Builder为空!")
                return
            }
            boolean multi = (builder instanceof MultiPropertyBuilder)
            try {
                if (multi) {
                    Map<String, Object> ps = ((MultiPropertyBuilder) builder).build(ctx)
                    log.debug("builder: {} populate propertyValues: {}", builder, ps)
                    ps.each { retObj[(it.key)] = it.value }
                } else {
                    if (!propName) {
                        log.warn("属性名为空, 忽略!")
                        return
                    }
                    Object value = builder.build(ctx)
                    log.debug("builder: {} populate value: {} for property: " + propName, builder, value)
                    retObj[(propName)] = value
                }
            } catch(Exception ex) {
                log.error("属性build错误. builder: " + builder + (multi ? (", propName: " + propName) : ""), ex)
            }
        }
        return retObj
    }


    ObjBuilder<T> add(String propName, Builder builder) {
        if (builder instanceof MultiPropertyBuilder && propName != null && !propName.isEmpty()) {
            log.warn("MultiPropertyGenerator 对应多个属性所以不需要有属性名: ({}), 请用add(MultiPropertyGenerator builder)", propName)
        }
        fieldGenerators.put(propName, builder)
        return this
    }


    ObjBuilder<T> add(MultiPropertyBuilder builder) {
        fieldGenerators.put(UUID.randomUUID().toString(), builder)
        return this
    }


    /**
     * 创建目前对象
     * @param ctx
     * @return
     */
    protected T instance(Map ctx) {
        T targetObj = null
        Class<T> targetClass = getJavaBeanClz()
        if (Map.class == (targetClass)) {
            targetObj = (T) new LinkedHashMap<>()
        } else if (Set.class == (targetClass)) {
            targetObj = (T) new LinkedHashSet<>()
        } else if (List.class == (targetClass)) {
            targetObj = (T) new ArrayList<>()
        } else {
            targetObj = targetClass.newInstance()
        }
        return targetObj
    }


    Class<T> getJavaBeanClz() {
        return targetClz
    }


    ObjBuilder setJavaBeanClz(Class<T> pDTOClass) {
        targetClz = pDTOClass
        return this
    }
}

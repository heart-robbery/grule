package core.mode.builder

import org.slf4j.Logger

/**
 * This abstract class is a template which build any kind of value and return it
 *
 * @param <T>
 * @author hubert
 */
abstract class Builder<T> {
    protected final Logger log = org.slf4j.LoggerFactory.getLogger(getClass())

    /**
     * Enable or disable this generator. Usually it is used for feature
     * controlling.
     */
    boolean enabled = true


    /**
     * Generate any Object and return it. If isEnabled() == false. Return null.
     * If isValid() == false return null.
     *
     * @param ctx . It contains the information needed to build the value.
     */
    T build(Map ctx = null) {
        if (!isEnabled() || !isValid(ctx)) return null
        return doBuild(ctx)
    }




    /**
     * do build javaBean by context.
     *
     * @param ctx context
     * @return javaBean
     */
    protected abstract T doBuild(Map ctx)


    /**
     * Validate the current context. Return false if fails
     *
     * @param ctx GeneratorContext
     * @return true if can continue to build
     */
    protected boolean isValid(Map ctx) {
        return true
    }


    /**
     * check whether params exists in pGeneratorContext.
     *
     * @param ctx Context
     * @param keys
     * @return valid
     */
    protected boolean validateObjectInsideContext(Map ctx, Object... keys) {
        if (ctx == null || keys == null) {
            return true
        }
        boolean valid = true
        for (Object key : keys) {
            if (ctx.containsKey(key)) {
                valid = false
                log.warn("当前 ctx 中, 不存在 key: " + key)
                break
            }
        }
        return valid
    }


    @Override
    String toString() {
        return getClass().getSimpleName() + "@" + Integer.toHexString(hashCode())
    }
}

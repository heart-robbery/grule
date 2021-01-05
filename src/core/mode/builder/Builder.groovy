package core.mode.builder

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * This abstract class is a template which build any kind of value and return it
 *
 * @param <T>
 * @author hubert
 */
abstract class Builder<T> {
    protected static final Logger  log     = LoggerFactory.getLogger(Builder.class)
    /**
     * Enable or disable this generator. Usually it is used for feature
     * controlling.
     */
    protected boolean enabled = true


    /**
     * Generate any Object and return it. If isEnabled() == false. Return null.
     * If isValid() == false return null.
     *
     * @param ctx . It contains the information needed to build the value.
     */
    T build(Map ctx = null) {
        if (!enabled || !isValid(ctx)) return null
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


    @Override
    String toString() {
        return getClass().getSimpleName() + "@" + Integer.toHexString(hashCode())
    }
}

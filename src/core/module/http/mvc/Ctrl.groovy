package core.module.http.mvc

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * 标明是个控制器(Controller)
 */
@Target([ElementType.TYPE])
@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface Ctrl {
    /**
     * 路径前缀
     * @return
     */
    String prefix() default ''
}
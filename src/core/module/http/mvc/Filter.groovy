package core.module.http.mvc

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

@Target([ElementType.METHOD])
@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface Filter {
    /**
     * 优先级 越大越先执行
     * @return
     */
    float order() default 0f
}
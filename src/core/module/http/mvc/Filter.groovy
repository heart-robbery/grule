package core.module.http.mvc

import java.lang.annotation.*

@Target([ElementType.METHOD])
@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface Filter {
    /**
     * 优先级 越大越先执行
     * @return
     */
    int order() default 0
}
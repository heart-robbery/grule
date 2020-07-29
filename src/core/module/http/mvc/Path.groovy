package core.module.http.mvc

import java.lang.annotation.*

@Target([ElementType.METHOD, ElementType.TYPE])
@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface Path {
    String path() default null
    String method() default null
    String consumer() default null
}
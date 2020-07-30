package core.module.http.mvc

import java.lang.annotation.*

@Target([ElementType.METHOD, ElementType.TYPE])
@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface Path {
    String path() default ''
    String method() default ''
    String consumer() default ''
}
package core.module.http.mvc

import java.lang.annotation.*

@Target([ElementType.METHOD])
@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface Path {
    String path()
    String method() default ''
    String consumer() default ''
}
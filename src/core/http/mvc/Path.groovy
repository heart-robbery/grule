package core.http.mvc

import java.lang.annotation.*


/**
 * 控制层 路径
 */
@Target([ElementType.METHOD])
@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface Path {
    /**
     * 接口路径
     * @return
     */
    String[] path()
    /**
     * get,post,delete
     * @return
     */
    String method() default ''
    /**
     * application/json, multipart/form-data, application/x-www-form-urlencoded
     * @return
     */
    String[] consumer() default []
}
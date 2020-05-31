package ctrl

import core.Utils
import core.module.ServerTpl
import io.netty.handler.codec.http.HttpResponseStatus
import ratpack.exec.Promise
import ratpack.form.Form
import ratpack.handling.Chain
import ratpack.handling.RequestId

import java.lang.annotation.*
import java.lang.reflect.Method
import java.util.function.Consumer

import static ctrl.common.ApiResp.fail
import static ctrl.common.ApiResp.ok

/**
 * ctrl 层模板类, 所有ctrl 层的类都要继承此类
 */
class CtrlTpl extends ServerTpl {
    // 前缀
    protected String prefix


    def init(Chain chain) {
        def fn = {Class clz ->
            Utils.iterateMethod(clz, { m ->
                if (m.parameterCount == 1 && m.parameterTypes[0] == Chain.class && 'init' != m.name) {
                    m.invoke(this, chain)
                } else if (m.getDeclaredAnnotation(Path) != null) {
                    create(chain, m)
                }
            })
        }
        if (prefix) {
            chain.prefix(prefix) {ch ->
                fn.call(this.getClass())
            }
        } else {
            fn.call(getClass())
        }
        this
    }

    protected void create(Chain chain, Method m) {
        def path = m.getAnnotation(Path)
        def ts = m.getParameterTypes()

        // 验证
        if (ts.find {t -> t instanceof Consumer} && !void.class.isAssignableFrom(m.returnType)) {
            throw new Exception("Method return type must be void")
        }

        if ('get'.equalsIgnoreCase(path.method())) {
            chain.get(path.path()) {ctx ->
                ctx.render Promise.async{ down ->
                    async {
                        try {
                            def fn = {o -> down.success(ok(o))} as Consumer
                            def o = m.invoke(this, ts.collect {t ->
                                if (Map.isAssignableFrom(t)) return ctx.request.queryParams
                                else if (RequestId.isAssignableFrom(t)) return ctx.get(RequestId.TYPE)
                                else if (Consumer.isAssignableFrom(t)) return fn
                                else throw new RuntimeException("$m.name 不支持的参数类型: " + t.simpleName)
                            }.toArray())
                            if (void.class.isAssignableFrom(m.getReturnType())) fn.accept(o)
                        } catch(Exception ex) {
                            down.success(fail(ex.message?:ex.class.simpleName))
                            log.error("", ex)
                        }
                    }
                }
            }
        }
        else if ('post'.equalsIgnoreCase(path.method()) && path.consume().contains('application/json')) {
            chain.post(path.path()) {ctx ->
                if (!ctx.request.contentType?.type?.contains(path.consume())) {
                    ctx.clientError(HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE.code())
                    return
                }
                ctx.parse(Map.class).then{ params ->
                    ctx.render Promise.async{ down ->
                        async {
                            try {
                                def fn = {o -> down.success(ok(o))} as Consumer
                                def o = m.invoke(this, ts.collect {t ->
                                    if (t instanceof Map) return [*:params, *:ctx.request.queryParams]
                                    else if (t instanceof RequestId) return ctx.get(RequestId.TYPE)
                                    else if (t instanceof Consumer) return fn
                                    else throw new RuntimeException("$m.name 不支持的参数类型: " + t.simpleName)
                                }.toArray())
                                if (void.class.isAssignableFrom(m.getReturnType())) fn.accept(o)
                            } catch(Exception ex) {
                                down.success(fail(ex.message?:ex.class.simpleName))
                                if (ex !instanceof IllegalArgumentException) log.error("", ex)
                            }
                        }
                    }
                }
            }
        }
        else if ('post'.equalsIgnoreCase(path.method()) && path.consume().contains('application/x-www-form-urlencoded')) {
            chain.post(path.path()) {ctx ->
                if (!ctx.request.contentType?.type?.contains(path.consume())) {
                    ctx.clientError(HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE.code())
                    return
                }
                ctx.parse(Form.class).then{ form ->
                    ctx.render Promise.async{ down ->
                        async {
                            try {
                                def fn = {o -> down.success(ok(o))} as Consumer
                                def o = m.invoke(this, ts.collect {t ->
                                    if (t instanceof Map) return [*:form, *:ctx.request.queryParams]
                                    else if (t instanceof RequestId) return ctx.get(RequestId.TYPE)
                                    else if (t instanceof Consumer) return fn
                                    else throw new RuntimeException("$m.name 不支持的参数类型: " + t.simpleName)
                                }.toArray())
                                if (void.class.isAssignableFrom(m.getReturnType())) fn.accept(o)
                            } catch(Exception ex) {
                                down.success(fail(ex.message?:ex.class.simpleName))
                                if (ex !instanceof IllegalArgumentException) log.error("", ex)
                            }
                        }
                    }
                }
            }
        }
        else if ('' == path.method()) {
            // TODO
        }
    }
}

@Target([ElementType.METHOD])
@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface Path {
    String method()
    String path()
    String consume() default ''
}
package ctrl

import core.Utils
import core.module.ServerTpl
import ctrl.common.ApiResp
import io.netty.handler.codec.http.HttpResponseStatus
import ratpack.exec.Promise
import ratpack.form.Form
import ratpack.handling.Chain
import ratpack.handling.Context

import java.nio.charset.Charset
import java.util.function.Function

/**
 * ctrl 层模板类, 所有ctrl 层的类都要继承此类
 */
class CtrlTpl extends ServerTpl {
    // 前缀
    protected String prefix


    CtrlTpl init(Chain chain) {
        def fn = {Class clz, Chain ch ->
            Utils.iterateMethod(clz, { m ->
                if (m.parameterCount == 1 && m.parameterTypes[0] == Chain.class && 'init' != m.name) {
                    m.invoke(this, ch)
                }
            })
        }
        if (prefix) {
            chain.prefix(prefix) {ch ->
                fn.call(this.getClass(), ch)
            }
        } else {
            fn.call(getClass(), chain)
        }
        this
    }


    /**
     * 接收json
     * @param ctx
     * @param fn
     */
    protected void json(Context ctx, Function<String, Object> fn) {
        if (!ctx.request.contentType.type.contains('application/json')) {
            ctx.clientError(HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE.code())
            return
        }
        ctx.request.body.then {data ->
            ctx.render Promise.async{ down ->
                async {
                    try {
                        down.success(ApiResp.ok(
                            fn.apply(data?.getText(Charset.forName('utf-8')))
                        ))
                    } catch (Exception ex) {
                        down.success(ApiResp.of(ctx['respCode']?:'01', (ex.class.name + (ex.message ? ": $ex.message" : ''))))
                        log.info("", ex)
                    }
                }
            }
        }
    }


    // 读数request body 里面的 字符串
    protected void string(Context ctx, Function<String, Object> fn) {
        ctx.request.body.then {data ->
            ctx.render Promise.async{ down ->
                async {
                    try {
                        down.success(ApiResp.ok(
                            fn.apply(data?.getText(Charset.forName('utf-8')))
                        ))
                    } catch (Exception ex) {
                        down.success(ApiResp.of(ctx['respCode']?:'01', (ex.class.name + (ex.message ? ": $ex.message" : ''))))
                        log.info("", ex)
                    }
                }
            }
        }
    }


    // 表单提交
    protected void form(Context ctx, Function<Form, Object> fn) {
        ctx.parse(Form).then {fd ->
            ctx.render Promise.async{ down ->
                async {
                    try {
                        down.success(ApiResp.ok(fn.apply(fd)))
                    } catch (Exception ex) {
                        down.success(ApiResp.of(ctx['respCode']?:'01', (ex.class.name + (ex.message ? ": $ex.message" : ''))))
                        log.info("", ex)
                    }
                }
            }
        }
    }


    protected void get(Context ctx, Function<Map, Object> fn) {
        ctx.render Promise.async{ down ->
            async {
                try {
                    down.success(ApiResp.ok(fn.apply(ctx.request.queryParams)))
                } catch (Exception ex) {
                    down.success(ApiResp.of(ctx['respCode']?:'01', (ex.class.name + (ex.message ? ": $ex.message" : ''))))
                    log.info("", ex)
                }
            }
        }
    }
}

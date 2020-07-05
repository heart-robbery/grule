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
import java.util.function.BiConsumer
import java.util.function.Consumer

/**
 * ctrl 层模板类, 所有ctrl 层的类都要继承此类
 */
class CtrlTpl extends ServerTpl {
    // 前缀
    protected String prefix


    void init(Chain chain) {
        def fn = {Class clz, Chain ch ->
            Utils.iterateMethod(clz, { m ->
                if (m.parameterCount == 1 && m.parameterTypes[0] == Chain && 'init' != m.name) {
                    m.invoke(this, ch)
                }
            })
        }
        if (prefix) {
            chain.prefix(prefix) {ch -> fn.call(this.getClass(), ch) }
        } else {
            fn.call(getClass(), chain)
        }
    }


    /**
     * 接收json
     * @param ctx
     * @param fn
     */
    protected void json(Context ctx, BiConsumer<String, Consumer<Object>> fn) {
        if (!ctx.request.contentType.type.contains('application/json')) {
            ctx.clientError(HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE.code())
            return
        }
        ctx.request.body.then {data ->
            ctx.render Promise.async{ down ->
                async {
                    try {
                        fn.accept(data?.getText(Charset.forName('utf-8')), {result -> down.success(result)} as Consumer)
                    } catch (Exception ex) {
                        down.success(ApiResp.of(ctx['respCode']?:'01', (ex.class.name + (ex.message ? ": $ex.message" : ''))))
                        log.info("", ex)
                    }
                }
            }
        }
    }


    // 读数request body 里面的 字符串
    protected void string(Context ctx, BiConsumer<String, Consumer<Object>> fn) {
        ctx.request.body.then {data ->
            ctx.render Promise.async{ down ->
                async {
                    try {
                        fn.accept(data?.getText(Charset.forName('utf-8')), {result -> down.success(result)} as Consumer)
                    } catch (Exception ex) {
                        down.success(ApiResp.of(ctx['respCode']?:'01', (ex.class.name + (ex.message ? ": $ex.message" : ''))))
                        log.info("", ex)
                    }
                }
            }
        }
    }


    // 表单提交
    protected void form(Context ctx, BiConsumer<Form, Consumer<Object>> fn) {
        ctx.parse(Form).then {fd ->
            ctx.render Promise.async{ down ->
                async {
                    try {
                        fn.accept(fd, {result -> down.success(result)} as Consumer)
                    } catch (Exception ex) {
                        down.success(ApiResp.of(ctx['respCode']?:'01', (ex.class.name + (ex.message ? ": $ex.message" : ''))))
                        log.info("", ex)
                    }
                }
            }
        }
    }


    // get 请求封装
    protected void get(Context ctx, BiConsumer<Map, Consumer<Object>> fn) {
        ctx.render Promise.async{ down ->
            async {
                try {
                    fn.accept(ctx.request.queryParams, {result -> down.success(result)} as Consumer)
                } catch (Exception ex) {
                    down.success(ApiResp.of(ctx['respCode']?:'01', (ex.class.name + (ex.message ? ": $ex.message" : ''))))
                    log.info("", ex)
                }
            }
        }
    }
}

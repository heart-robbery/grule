package module

import cn.xnatural.enet.common.Utils
import cn.xnatural.enet.event.EL
import cn.xnatural.enet.server.ServerTpl
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature
import ctrl.common.ApiResp
import ctrl.common.FileData
import io.netty.handler.codec.http.HttpResponseStatus
import ratpack.error.internal.ErrorHandler
import ratpack.form.Form
import ratpack.handling.Chain
import ratpack.handling.Context
import ratpack.handling.RequestId
import ratpack.render.RendererSupport
import ratpack.server.BaseDir
import ratpack.server.RatpackServer
import sevice.FileUploader

import javax.annotation.Resource
import java.time.Duration
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

import static cn.xnatural.enet.common.Utils.isEmpty

class RatpackWeb extends ServerTpl {
    @Resource
    Executor exec

    RatpackServer srv;

    RatpackWeb() { super('ratpack') }


    @EL(name = 'sys.starting')
    def start() {
        attrs.putAll((Map<? extends String, ?>) ep.fire("env.ns", 'http', getName()));
        attr("session.enabled", Utils.toBoolean(ep.fire("env.getAttr", "session.enabled"), false));
        srv = RatpackServer.start({ srv ->
            srv.with {
                serverConfig({ builder ->
                    builder.with {
                        port(getInteger('port', 8080))
                        threads(1)
                        connectTimeoutMillis(1000 * 10)
                        idleTimeout(Duration.ofSeconds(10))
                        sysProps()
                        registerShutdownHook(false)
                        baseDir(BaseDir.find('static/'))
                        development(false)
                    }
                })
                handlers({initChain(it)})
            }
        })
        ep.fire('web.started')
    }

    @EL(name = 'sys.stopping')
    def stop() {
        srv?.stop()
    }

    // 注册handler
    def initChain(Chain chain) {
        chain.with {
            register({ rs ->
                rs.with{
                    // 接口返回json格式
                    add(new RendererSupport<ApiResp>() {
                        @Override
                        void render(Context ctx, ApiResp resp) throws Exception {
                            ctx.response.contentType('application/json')
                            resp.reqId = ctx.get(RequestId.TYPE).toString()
                            ctx.response.send(JSON.toJSONString(resp, SerializerFeature.WriteMapNullValue))
                        }
                    })
                    // 错误处理
                    add(new ErrorHandler() {
                        @Override
                        void error(Context ctx, int code) throws Exception {
                            log.warn("Request Warn '{}', status: {}, path: " + ctx.request.uri, ctx.get(RequestId.TYPE), code)
                            ctx.response.status(code).send();
                        }

                        @Override
                        void error(Context ctx, Throwable ex) throws Exception {
                            log.error(ex, "Request Error '{}', path: {}", ctx.get(RequestId.TYPE), ctx.request.uri)
                            ctx.response.status(500).send();
                        }
                    })
                }
            })

            all({ctx ->
                log.info("Process Request '{}': {}", ctx.get(RequestId.TYPE), ctx.request.uri)
                if (getBoolean("session.enabled", false)) { // 添加session控制
                    def sId = ctx.request.oneCookie('sId');
                    sId = sId?:UUID.randomUUID().toString().replace('-', '')
                    ep.fire("session.access", sId)
                    def c = ctx.response.cookie('sId', sId)
                    c.maxAge = TimeUnit.MINUTES.toSeconds((Integer) ep.fire("session.getExpire") + 5)
                }
                ctx.next()
            })

            // 主页
            get('') { ctx -> ctx.render ctx.file('static/index.html') }

            // 测试抛出错误
            get('error') {ctx ->
                throw new RuntimeException('xxxxxxxxxxxx')
            }

            // 接收form 表单提交
            post('form', {ctx ->
                ctx.parse(Form.class).then({ form ->
                    // form.file('').fileName // 提取上传的文件
                    ctx.render ApiResp.ok(form.values())
                })
            })

            // 文件上传
            post('upload', {ctx ->
                if (!ctx.request.contentType.type.contains('multipart/form-data')) {
                    ctx.clientError(HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE.code())
                    return
                }
                ctx.parse(Form.class).then({form ->
                    def fu = bean(FileUploader.class);
                    def ls = fu.save(
                        form.files().values().collect{f -> new FileData(originName: f.fileName, inputStream: f.inputStream)}
                    ).collect {f -> fu.toFullUrl(f.resultName)}
                    // 返回上的文件的访问地址
                    ctx.render ApiResp.ok(ls)
                })
            })
            // 获取上传的文件
            get('file/:fName') {ctx ->
                ctx.response.cookie('Cache-Control', "max-age=60")
                ctx.render bean(FileUploader.class).findFile(ctx.pathTokens.fName).toPath()
            }

            post('json') {ctx ->
                if (!ctx.request.contentType.type.contains('application/json')) {
                    ctx.clientError(HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE.code())
                    return
                }
                ctx.parse(Map.class).then({ m ->
                    ctx.render ApiResp.ok(m)
                })
            }

            // 依次从外往里执行多个handler, 例: pre/sub
            prefix('pre') {ch ->
                ch.with {
                    all({ctx ->
                        println('xxxxxxxxxxxxxxxx')
                        ctx.next()
                    })
                    get('sub', { ctx -> ctx.render 'pre/sub' })
                    get('sub2', { ctx -> ctx.render 'pre/sub2' })
                }
            }

            get("js/:fName") { ctx ->
                ctx.response.cookie('Cache-Control', "max-age=60")
                ctx.render ctx.file("static/js/$ctx.pathTokens.fName")
            }
            get("css/:fName") {ctx ->
                ctx.response.cookie('Cache-Control', "max-age=60")
                ctx.render ctx.file("static/css/$ctx.pathTokens.fName")
            }
        }
    }


    @EL(name = 'http.getHp', async = false)
    def getHp() {
        srv.bindHost + ':' + srv.bindPort
    }
}

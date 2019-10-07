package ctrl

import cn.xnatural.enet.event.EL
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature
import core.module.EhcacheSrv
import core.module.ServerTpl
import ctrl.common.ApiResp
import org.ehcache.Cache
import ratpack.error.internal.ErrorHandler
import ratpack.handling.Chain
import ratpack.handling.Context
import ratpack.handling.RequestId
import ratpack.render.RendererSupport
import ratpack.server.BaseDir
import ratpack.server.RatpackServer
import ratpack.util.internal.TransportDetector

import javax.annotation.Resource
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class RatpackWeb extends ServerTpl {
    @Resource
    Executor exec
    RatpackServer srv
    @Lazy protected List<CtrlTpl> ctrls = new LinkedList<>()


    RatpackWeb() { super('web') }


    @EL(name = 'sys.starting')
    def start() {
        TransportDetector.NioTransport.metaClass.invokeMethod = {String name, args
            println '======================'
        }
        srv = RatpackServer.start({ srv ->
            srv.serverConfig({ builder ->
                builder.with {
                    port(attrs.port?:8080)
                    threads(1)
                    connectTimeoutMillis(1000 * 10)
                    idleTimeout(Duration.ofSeconds(10))
                    sysProps()
                    registerShutdownHook(false)
                    baseDir(BaseDir.find('static/'))
                    development(false)
                }
            })
            srv.handlers{initChain(it)}
        })
        ep.fire('web.started')
    }


    @EL(name = 'sys.stopping')
    def stop() {
        srv?.stop()
    }


    @EL(name = 'sys.started')
    def started() {
        ctrls.each {ep.fire('inject', it)}
    }


    /**
     * 添加 ctrl 层类
     * @param clzs
     * @return
     */
    def ctrls(Class<CtrlTpl>...clzs) {
        if (!clzs) return this
        clzs.each {clz -> ctrls.add(clz.newInstance())}
        this
    }


    // 初始化Chain, 注册handler
    def initChain(Chain chain) {
        chain.register{rs ->
            // 接口返回json格式
            rs.add(new RendererSupport<ApiResp>() {
                @Override
                void render(Context ctx, ApiResp resp) throws Exception {
                    ctx.response.contentType('application/json')
                    resp.reqId = ctx.get(RequestId.TYPE).toString()
                    ctx.response.send(JSON.toJSONString(resp, SerializerFeature.WriteMapNullValue))
                }
            })


            // 错误处理
            rs.add(new ErrorHandler() {
                @Override
                void error(Context ctx, int code) throws Exception {
                    log.warn("Request Warn '{}', status: {}, path: " + ctx.request.uri, ctx.get(RequestId.TYPE), code)
                    ctx.response.status(code).send()
                }

                @Override
                void error(Context ctx, Throwable ex) throws Exception {
                    log.error("Request Error '" + ctx.get(RequestId.TYPE) + "', path: " + ctx.request.uri, ex)
                    ctx.render ApiResp.fail(ex.getMessage())
                    // ctx.response.status(500).send()
                }
            })
        }

        // 拦截器
        chain.all({ctx ->
            log.info("Process Request '{}': {}", ctx.get(RequestId.TYPE), ctx.request.uri)
            if (attrs.session.enabled) session(ctx)
            ctx.next()
        })

        ctrls.each {ctrl ->
            ep.fire('inject', ctrl)
            ctrl.init(chain)
        }
    }

    // 处理session
    def session(Context ctx) {
        def sId = ctx.request.oneCookie('sId')?:UUID.randomUUID().toString().replace('-', '')
        ctx.metaClass.sId = sId
        def c = ctx.response.cookie('sId', sId)
        c.maxAge = TimeUnit.MINUTES.toSeconds(attrs.session.expire?:30 + 2)

        if ('redis' == attrs.session.type) {

        } else {
            // session的数据, 用ehcache 保存 session 数据
            def sData = ep.fire("${EhcacheSrv.F_NAME}.get", 'session', sId)
            if (sData) {
                sData.accessTime = System.currentTimeMillis()
                ctx.metaClass.sData = sData
            } else {
                synchronized (this) {
                    sData = ep.fire("${EhcacheSrv.F_NAME}.get", 'session', sId)
                    if (!sData) {
                        sData = new ConcurrentHashMap()
                        sData.id = sId
                        sData.accessTime = System.currentTimeMillis()
                        Cache cache = ep.fire("${EhcacheSrv.F_NAME}.create", 'session', Duration.ofMinutes(attrs.session.expire?:30L), attrs.session.maxLimit?:100000)
                        cache.put(sId, sData)
                        ctx.metaClass.sData = sData
                        log.info("New session '{}' at {}", sId, sData.accessTime)
                    }
                }
            }
        }
    }


    @EL(name = 'http.getHp', async = false)
    def getHp() { srv.bindHost + ':' + srv.bindPort }
}

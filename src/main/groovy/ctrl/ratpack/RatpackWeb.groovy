package ctrl.ratpack

import cn.xnatural.enet.event.EL
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature
import core.Devourer
import core.Utils
import core.module.EhcacheSrv
import core.module.RedisClient
import core.module.ServerTpl
import ctrl.CtrlTpl
import ctrl.common.ApiResp
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpRequest
import org.ehcache.Cache
import ratpack.error.internal.ErrorHandler
import ratpack.func.Action
import ratpack.handling.Chain
import ratpack.handling.Context
import ratpack.handling.Handler
import ratpack.handling.RequestId
import ratpack.impose.Impositions
import ratpack.render.RendererSupport
import ratpack.server.BaseDir
import ratpack.server.RatpackServer
import ratpack.server.internal.DefaultRatpackServer
import ratpack.server.internal.NettyHandlerAdapter
import ratpack.service.internal.DefaultEvent
import ratpack.service.internal.ServicesGraph

import javax.annotation.Resource
import java.security.AccessControlException
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.Map.Entry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.LongAdder

class RatpackWeb extends ServerTpl {
    @Resource
    Executor                      exec
    RatpackServer                 srv
    @Lazy protected List<CtrlTpl> ctrls = new LinkedList<>()
    /**
     * 吞噬器.请求执行控制器
     */
    protected Devourer devourer


    RatpackWeb() { super('web') }


    @EL(name = 'sys.starting')
    def start() {
        if (srv) throw new RuntimeException("$name is already running")
//        TransportDetector.NioTransport.metaClass.invokeMethod = {String name, args
//            println '======================'
//        }

        // 初始化请求执行控制器
        devourer = new Devourer(getClass().simpleName, exec)
        devourer.pause{
            // 判断线程池里面等待执行的任务是否过多(避免让线程池里面全是请求任务在执行)
            if (Utils.findMethod(exec.getClass(), "getWaitingCount").invoke(exec) >
                Math.min(Runtime.getRuntime().availableProcessors(), Utils.findMethod(exec.getClass(), "getCorePoolSize").invoke(exec) * 2)
            ) return true
            return false
        }

        // 入口handler ratpack.server.internal.NettyHandlerAdapter
        srv = new DefaultRatpackServer({ srv ->
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
        } as Action, Impositions.current()) {
            @Override
            protected NettyHandlerAdapter buildAdapter(DefaultRatpackServer.DefinitionBuild definition) throws Exception {
                serverRegistry = buildServerRegistry(definition.getServerConfig(), definition.getUserRegistryFactory())

                Handler ratpackHandler = buildRatpackHandler(serverRegistry, definition.getHandlerFactory())
                ratpackHandler = decorateHandler(ratpackHandler, serverRegistry)

                servicesGraph = new ServicesGraph(serverRegistry)
                servicesGraph.start(new DefaultEvent(serverRegistry, reloading))
                // new RatpackHander(RatpackWeb.this, serverRegistry, ratpackHandler)
                new NettyHandlerAdapter(serverRegistry, ratpackHandler) {
                    @Override
                    void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        if (msg instanceof HttpRequest) {count()}
                        super.channelRead(ctx, msg)
                    }
                }
            }
        }
        // srv = RatpackServer.start()
        srv.start()
        ep.fire('web.started')
    }


    @EL(name = 'sys.stopping')
    def stop() { srv?.stop() }


    @EL(name = 'sys.started')
    def started() { ctrls.each {ep.fire('inject', it)} }


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
                    log.debug("End Request '{}', response: {}", resp.reqId, resp)
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
                    ctx.render new ApiResp(success: false, desc: ex.getMessage())
                    // ctx.response.status(500).send()
                }
            })
        }

        def ignoreSuffix = new HashSet(['.js', '.css', '.html', '.png', '.ttf', '.woff', '.woff2', 'favicon.ico', '.js.map', *attrs.ignorePrintUrlSuffix?:[]])
        // 请求预处理
        chain.all({ctx ->
            // 限流? TODO
            // 打印请求
            if (!ignoreSuffix.find{ctx.request.path.endsWith(it)}) {
                log.info("Start Request '{}': {}", ctx.get(RequestId.TYPE), ctx.request.uri)
            }
            // session处理
            if (attrs.session.enabled) session(ctx)
            // 权限验证
            ctx.metaClass.auth = {String lowestRole -> // 需要的最低角色
                String uRoles = ctx.sData?.uRoles // 当前用户的角色. 例: role1,role2,role3
                if (!uRoles) throw new AccessControlException('需要登录获取权限')
                if (uRoles.contains(lowestRole)) return true
                String lowestLevel // 对应最低需要角色的等级
                Set<String> uLevels = new HashSet(7) // 当前用户所有角色的等级
                attrs.role.each {Entry<String, Collection> e ->
                    if (e.key.startsWith('level')) {
                        e.value.each {r ->
                            if (r == lowestRole) lowestLevel = e.key
                            if (uRoles.contains(r)) uLevels << e.key
                        }
                    }
                }
                // 如果当前用户的所有角色等级都没找到大于最低角色等级的就抛错
                if (!uLevels.find {uLevel -> uLevel.compareToIgnoreCase(lowestLevel) > 0 ? true : false }) {
                    throw new AccessControlException('没有权限')
                }
            }
            ctx.next()
        })

        ctrls.each {ctrl ->
            ep.fire('inject', ctrl)
            ctrl.init(chain)
        }
    }


    @Lazy RedisClient redis = bean(RedisClient)
    @Lazy EhcacheSrv ehcache = bean(EhcacheSrv)
    // 处理session
    def session(Context ctx) {
        def sId = ctx.request.oneCookie('sId')?:UUID.randomUUID().toString().replace('-', '')
        ctx.metaClass.sId = sId
        def c = ctx.response.cookie('sId', sId)
        c.maxAge = ((Duration) attrs.session.expire).seconds?:Duration.ofMinutes(32L).seconds

        if ('redis' == attrs.session.type) { // session的数据, 用redis 保存 session 数据
            if (redis == null) throw new RuntimeException('RedisClient is not exist')
            def sData = new Expando(new ConcurrentHashMap()) {
                @Override
                Object getProperty(String pName) {
                    def v = super.getProperty(pName)
                    if (v != null) return v
                    else {
                        // 取不到就从redis 里面取
                        v = redis.hget("session:$sId", pName)
                        getProperties().put(pName, v)
                    }
                    v
                }

                @Override
                void setProperty(String pName, Object newValue) {
                    // 先更新到redis中
                    redis.hset("session:$sId", pName, newValue, (attrs.session.expire?:Duration.ofMinutes(30)).seconds.intValue())
                    super.setProperty(pName, newValue)
                }
            }
            ctx.metaClass.sData = sData
            if (redis.exists("session:$sId")) {
                sData.id = sId
            } else {
                synchronized (this) {
                    if (!redis.exists("session:$sId")) {
                        sData.id = sId
                        log.info("New session '{}'", sId)
                    }
                }
            }
        } else {// session的数据, 默认用ehcache 保存 session 数据
            if (ehcache == null) throw new RuntimeException('EhcacheSrv is not exist')
            def sData = ehcache.get('session', sId)
            if (sData) {
                ctx.metaClass.sData = sData
            } else {
                synchronized (this) {
                    sData = ehcache.get('session', sId)
                    if (!sData) {
                        sData = new ConcurrentHashMap()
                        sData.id = sId
                        Cache cache = ehcache.getOrCreateCache('session', attrs.session.expire?:Duration.ofMinutes(30L), attrs.session.maxLimit?:100000, null)
                        cache.put(sId, sData)
                        ctx.metaClass.sData = sData
                        log.info("New session '{}'", sId)
                    }
                }
            }
        }
    }


    protected Map<String, LongAdder> hourCount = new ConcurrentHashMap<>(3)
    // 请求统计
    protected def count() {
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH")
        boolean isNew = false
        String hStr = sdf.format(new Date())
        LongAdder count = hourCount.get(hStr)
        if (count == null) {
            synchronized (hourCount) {
                count = hourCount.get(hStr)
                if (count == null) {
                    count = new LongAdder(); hourCount.put(hStr, count)
                    isNew = true
                }
            }
        }
        count.increment()
        if (isNew) {
            final Calendar cal = Calendar.getInstance();
            cal.setTime(new Date())
            cal.add(Calendar.HOUR_OF_DAY, -1)
            String lastHour = sdf.format(cal.getTime())
            LongAdder c = hourCount.remove(lastHour)
            if (c != null) log.info("{} 时共处理 http 请求: {} 个", lastHour, c)
        }
    }


    @EL(name = 'http.getHp', async = false)
    def getHp() { srv.bindHost + ':' + srv.bindPort }
}

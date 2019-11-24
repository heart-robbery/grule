package ctrl.ratpack

import cn.xnatural.enet.event.EL
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature
import core.Devourer
import core.module.EhcacheSrv
import core.module.RedisClient
import core.module.ServerTpl
import ctrl.CtrlTpl
import ctrl.common.ApiResp
import org.ehcache.Cache
import ratpack.error.internal.ErrorHandler
import ratpack.handling.Chain
import ratpack.handling.Context
import ratpack.handling.RequestId
import ratpack.render.RendererSupport
import ratpack.server.BaseDir
import ratpack.server.RatpackServer

import javax.annotation.Resource
import java.security.AccessControlException
import java.text.SimpleDateFormat
import java.time.Clock
import java.time.Duration
import java.util.Map.Entry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
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
        srv = RatpackServer.start({ srv ->
            srv.serverConfig({ builder ->
                builder.with {
                    port(Integer.valueOf(attrs.port?:8080))
                    threads(Integer.valueOf(attrs['thread']?:1))
                    connectTimeoutMillis(1000 * 10)
                    idleTimeout(Duration.ofSeconds(10))
                    maxContentLength(Integer.valueOf(attrs.maxContentLength?:(1024 * 1024 * 10))) // 10M 文件上传大小限制
                    sysProps()
                    registerShutdownHook(false)
                    baseDir(BaseDir.find('static/'))
                    development(Boolean.valueOf(attrs['development']?:false))
                }
            })
            srv.handlers{initChain(it)}
        })
        ep.fire('web.started')
    }


    @EL(name = 'sys.stopping', async = false)
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
                    resp.seqNo = ctx.get(RequestId.TYPE).toString()
                    resp.cus = ctx.request.queryParams.cus // 原样返回的参数
                    def jsonStr = JSON.toJSONString(resp, SerializerFeature.WriteMapNullValue)

                    // 接口超时监控
                    def spend = ctx.get(Clock).instant().minusMillis(ctx.request.timestamp.toEpochMilli()).toEpochMilli()
                    if (spend > (Long.valueOf(attrs.warnRequestTime?:5000))) {
                        log.warn("End Request '" + resp.seqNo + "', path: " + ctx.request.uri + " , spend: " + spend + "ms, response: " + jsonStr)
                    } else {
                        log.debug("End Request '" + resp.seqNo + "', path: " + ctx.request.uri + " , spend: " + spend + "ms, response: " + jsonStr)
                    }

                    ctx.response.send(jsonStr) // 返回给客户端
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
                    if (ex instanceof AccessControlException) {
                        log.error("Request Error '" + ctx.get(RequestId.TYPE) + "', path: " + ctx.request.uri + ", " + ex.getMessage())
                    } else {
                        log.error("Request Error '" + ctx.get(RequestId.TYPE) + "', path: " + ctx.request.uri, ex)
                    }
                    ctx.render ApiResp.fail(ctx['respCode']?:'01', ex.getMessage()?:ex.getClass().simpleName)
                    // ctx.response.status(500).send()
                }
            })
        }

        def ignoreSuffix = new HashSet(['.js', '.css', '.html', '.vue', '.png', '.ttf', '.woff', '.woff2', 'favicon.ico', '.js.map', *attrs.ignorePrintUrlSuffix?:[]])
        // 请求预处理
        chain.all({ctx ->
            ctx.metaClass.propertyMissing = {String name -> null} // 随意访问不存在的属性不报错
            // 限流? TODO
            // 打印请求
            if (!ignoreSuffix.find{ctx.request.path.endsWith(it)}) {
                log.info("Start Request '{}': {}", ctx.get(RequestId.TYPE), ctx.request.uri)
            }
            // 统计
            count()
            // session处理
            if (attrs.session?.enabled) session(ctx)
            // 添加权限验证方法
            ctx.metaClass.auth = {String lowestRole -> auth(ctx, lowestRole)}
            ctx.next()
        })

        // 添加所有Controller层
        ctrls.each {ctrl ->
            ep.fire('inject', ctrl)
            ctrl.init(chain)
        }
    }


    // 权限验证
    def auth(Context ctx, String lowestRole) {
        if (!lowestRole) throw new IllegalArgumentException('lowestRole is empty')
        Set<String> uRoles = ctx.sData?.uRoles // 当前用户的角色. 例: role1,role2,role3
        if (!uRoles) {ctx.metaClass['respCode'] = '100'; throw new AccessControlException('需要登录')}
        if (uRoles.contains(lowestRole)) return true

        // 递归查找
        def recursiveList
        def recursiveMap
        recursiveList = {Collection root, String mathRole, AtomicInteger count = new AtomicInteger(0), Integer limit = 10 ->
            if (!root) return null
            if (count.getAndIncrement() >= limit) return null // 防止死递归
            for (Iterator it = root.iterator(); it.hasNext(); ) {
                def item = it.next()
                if (mathRole == item) return item // 返回匹配的角色名
                else if (item instanceof Map) return recursiveMap(item, mathRole, count, limit)
                else if (item instanceof Collection) return recursiveList(item, mathRole, count, limit)
            }
            null
        }
        recursiveMap = {Map root, String mathRole, AtomicInteger count = new AtomicInteger(0), Integer limit = 10 ->
            if (!root) return null
            if (count.getAndIncrement() >= limit) return null // 防止死递归
            for (Iterator it = root.iterator(); it.hasNext(); ) {
                Entry e = it.next()
                if (e.key == mathRole) return e.value
                else if (e.value instanceof Collection) return recursiveList(e.value, mathRole, count, limit)
                else if (e.value instanceof Map) return recursiveMap(e.value, mathRole, count, limit)
            }
        }

        if (uRoles.find {uRole -> // 用户角色
            ((Map) attrs['role'])?.find {e ->
                if (uRole == e.key) {
                    if (e.value instanceof Collection) {
                        if (recursiveList(e.value, lowestRole)) return true
                    } else if (e.value instanceof Map) {
                        if (recursiveMap(e.value, lowestRole)) return true
                    }
                } else {
                    if (e.value instanceof Collection) {
                        def v = recursiveList(e.value, uRole)
                        if (v instanceof Collection) {
                            if (recursiveList(v, lowestRole)) return true
                        } else if (v instanceof Map) {
                            if (recursiveMap(v, lowestRole)) return true
                        }
                    } else if (e.value instanceof Map) {
                        def v = recursiveMap(e.value, uRole)
                        if (v instanceof Collection) {
                            if (recursiveList(v, lowestRole)) return true
                        } else if (v instanceof Map) {
                            if (recursiveMap(v, lowestRole)) return true
                        }
                    }
                }
            }
        }) {
            uRoles.add(lowestRole) // 缓存被验证过的权限角色
            return true
        }
        throw new AccessControlException('没有权限')
    }


//    def oldAuth(Context ctx, String lowestRole) {
//        String uRoles = ctx.sData?.uRoles // 当前用户的角色. 例: role1,role2,role3
//        if (!uRoles) throw new AccessControlException('需要登录获取权限')
//        if (uRoles.contains(lowestRole)) return true
//        String lowestLevel // 对应最低需要角色的等级
//        Set<String> uLevels = new HashSet(7) // 当前用户所有角色的等级
//        attrs.role.each {Entry<String, Collection> e ->
//            if (e.key.startsWith('level_')) {
//                e.value.each {r ->
//                    if (r == lowestRole) lowestLevel = e.key
//                    if (uRoles.contains(r)) uLevels << e.key
//                }
//            }
//        }
//        // 如果当前用户的所有角色等级都没找到大于最低角色等级的就抛错
//        if (!uLevels.find {String uLevel ->
//            Integer.valueOf(uLevel.replace('level_', '')) > Integer.valueOf(lowestLevel.replace('level_', ''))
//        }) {
//            throw new AccessControlException('没有权限')
//        }
//    }


    @Lazy RedisClient redis = bean(RedisClient)
    @Lazy EhcacheSrv ehcache = bean(EhcacheSrv)
    // 处理session
    def session(Context ctx) {
        def sId = ctx.request.oneCookie('sId')

        if ('redis' == attrs.session?.type) { // session的数据, 用redis 保存 session 数据
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
                    redis.hset("session:$sId", pName, newValue, sessionExpire.seconds.intValue())
                    super.setProperty(pName, newValue)
                }
            }
            ctx.metaClass.sData = sData
            if (sId && redis.exists("session:$sId")) {
                sData.id = sId
            } else {
                sData.id = sId = UUID.randomUUID().toString().replace('-', '')
                log.info("New session '{}'", sId)
            }
        } else {// session的数据, 默认用ehcache 保存 session 数据
            if (ehcache == null) throw new RuntimeException('EhcacheSrv is not exist')
            def sData = (sId ? ehcache.get('session', sId) : null)
            if (sData == null) {
                sData = new ConcurrentHashMap()
                sData.id = sId = UUID.randomUUID().toString().replace('-', '')
                Cache cache = ehcache.getOrCreateCache('session', sessionExpire, Integer.valueOf(attrs.session?.maxLimit?:100000), null)
                cache.put(sId, sData)
                log.info("New session '{}'", sId)
            }
            ctx.metaClass.sData = sData
        }

        ctx.metaClass.sId = sId
        def c = ctx.response.cookie('sId', sId)
        c.maxAge = sessionExpire.plusSeconds(30).seconds
    }


    // 每小时统计请求的次数
    protected final Map<String, LongAdder> hourCount = new ConcurrentHashMap<>(3)
    // 请求统计
    protected def count() {
        SimpleDateFormat sdf = new SimpleDateFormat('MM-dd HH')
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
            final Calendar cal = Calendar.getInstance()
            cal.setTime(new Date())
            cal.add(Calendar.HOUR_OF_DAY, -1)
            String lastHour = sdf.format(cal.getTime())
            LongAdder c = hourCount.remove(lastHour)
            if (c) log.info('{} 时共处理 http 请求: {} 个', lastHour, c)
        }
    }


    @EL(name = ['http.hp', 'web.hp'], async = false)
    def getHp() { srv.bindHost + ':' + srv.bindPort }


    @EL(name = 'web.sessionExpire', async = false)
    Duration getSessionExpire() {
        if (attrs.session?.expire instanceof Duration) return attrs.session?.expire
        else if (attrs.session?.expire instanceof Number || attrs.session?.expire instanceof String) return Duration.ofMinutes(Long.valueOf(attrs.session?.expire))
        Duration.ofMinutes(30) // 默认session 30分钟
    }
}

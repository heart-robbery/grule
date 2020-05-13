package ctrl.ratpack

import cn.xnatural.enet.event.EL
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature
import core.module.EhcacheSrv
import core.module.RedisClient
import core.module.ServerTpl
import ctrl.CtrlTpl
import ctrl.common.ApiResp
import io.netty.handler.ssl.SslContextBuilder
import ratpack.error.internal.ErrorHandler
import ratpack.handling.Chain
import ratpack.handling.Context
import ratpack.handling.RequestId
import ratpack.http.Request
import ratpack.registry.RegistrySpec
import ratpack.render.RendererSupport
import ratpack.server.BaseDir
import ratpack.server.RatpackServer
import sun.security.x509.*

import java.security.AccessControlException
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.time.Clock
import java.time.Duration
import java.util.Map.Entry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.LongAdder

import static core.Utils.ipv4

class RatpackWeb extends ServerTpl {
    protected RatpackServer       srv
    protected final List<CtrlTpl> ctrls       = new LinkedList<>()
    // 是否可用
    boolean                       enabled     = false
    @Lazy def                     redis       = bean(RedisClient)
    @Lazy def                     ehcache     = bean(EhcacheSrv)


    RatpackWeb() { super('web') }


    @EL(name = 'sys.starting', async = true)
    def start() {
        if (srv) throw new RuntimeException("$name is already running")
        srv = RatpackServer.start({ srv ->
            srv.serverConfig({ builder ->
                builder.with {
                    threads(getInteger('thread', 1))
                    connectTimeoutMillis(1000 * 10)
                    // idleTimeout(Duration.ofSeconds(10)) websocket 是长连接
                    maxContentLength(getInteger('maxContentLength', 1024 * 1024 * 10)) // 10M 文件上传大小限制
                    sysProps()
                    registerShutdownHook(false)
                    baseDir(BaseDir.find('static/'))
                    development(getBoolean('development', false))

                    def addr = getStr('address', null)
                    if (addr) { address(InetAddress.getByName(addr)) }

                    if (getBoolean("secure", false) || getBoolean("ssl", false)) {
                        def se = security()
                        log.info("publicKey: " + Base64.encoder.encodeToString(se.v2.publicKey.encoded))
                        ssl(SslContextBuilder.forServer(se.v1, se.v2).build())
                        port(getInteger('port', 8443))
                    } else {
                        port(getInteger('port', 8080))
                    }
                }
            })

            // 注册常用处理器
            srv.registryOf { register(it) }

            // 初始化handlers
            srv.handlers { initChain(it) }
        })
        ep.fire('web.started')
    }


    @EL(name = 'sys.stopping', async = false)
    def stop() { srv?.stop() }


    @EL(name = 'sys.started', async = true)
    protected started() { ctrls.each {ep.fire('inject', it)}; enabled = true }


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


    // 注册常用处理器
    protected void register(RegistrySpec rs) {
        // 请求id生成器
        rs.add(RequestId.Generator.TYPE, new RequestId.Generator() {
            @Override
            RequestId generate(Request req) {
                String value
                def reqIdHeaderName = getStr("reqIdHeaderName", null)
                if (reqIdHeaderName) {
                    value = req.getHeaders().get(reqIdHeaderName)
                }
                if (value == null) value = UUID.randomUUID().toString().replaceAll("-", '')
                return RequestId.of(value)
            }
        })

        // 接口返回json格式
        rs.add(new RendererSupport<ApiResp>() {
            @Override
            void render(Context ctx, ApiResp resp) throws Exception {
                ctx.response.contentType('application/json')
                resp.traceNo = ctx.get(RequestId.TYPE).toString()
                def jsonStr = JSON.toJSONString(resp, SerializerFeature.WriteMapNullValue)

                // 接口超时监控
                def spend = ctx.get(Clock).instant().minusMillis(ctx.request.timestamp.toEpochMilli()).toEpochMilli()
                if (spend > getLong("warnRequestTime", 5000)) {
                    log.warn("End Request '" + resp.traceNo + "', path: " + ctx.request.uri + " , spend: " + spend + "ms, response: " + jsonStr)
                } else {
                    log.debug("End Request '" + resp.traceNo + "', path: " + ctx.request.uri + " , spend: " + spend + "ms, response: " + jsonStr)
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
                ctx.render ApiResp.of(ctx['respCode']?:'01', (ex.class.name + (ex.message ? ": $ex.message" : '')))
            }
        })
    }


    // 初始化Chain, 注册handler
    protected void initChain(Chain chain) {
        def ignoreSuffix = new HashSet(['.js', '.css', '.html', '.vue', '.png', '.ttf', '.woff', '.woff2', 'favicon.ico', '.js.map', *attrs()['ignorePrintUrlSuffix']?:[]])
        // 请求预处理
        chain.all({ctx ->
            if (!enabled) {
                ctx.response.status(503).send("请稍后再试")
                return
            }
            ctx.metaClass['propertyMissing'] = {String name -> null} // 随意访问不存在的属性不报错
            // 限流? TODO
            // 打印请求
            if (!ignoreSuffix.find{ctx.request.path.endsWith(it)}) {
                log.info("Start Request '{}': {}. from: " + ctx.request.remoteAddress.host, ctx.get(RequestId.TYPE), ctx.request.uri)
            }
            // 统计
            count()
            // session处理
            if (getBoolean('session.enabled', false)) session(ctx)
            // 添加权限验证方法
            ctx.metaClass['auth'] = {String lowestRole -> auth(ctx, lowestRole)}
            ctx.next()
        })

        // 添加所有Controller层
        ctrls.each { ctrl ->
            ep.addListenerSource(ctrl)
            ep.fire('inject', ctrl)
            ctrl.init(chain)
        }
    }


    // 权限验证
    protected auth(Context ctx, String lowestRole) {
        if (!lowestRole) throw new IllegalArgumentException('lowestRole is empty')
        Set<String> uRoles = ctx['sData']['uRoles'] // 当前用户的角色. 例: role1,role2,role3
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
            ((Map) attrs()['role'])?.find {e ->
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


    // 处理session
    protected session(Context ctx) {
        def sId = ctx.request.oneCookie('sId')

        if ('redis' == getStr('session.type')) { // session的数据, 用redis 保存 session 数据
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
            ctx.metaClass['sData'] = sData
            if (sId && redis.exists("session:$sId")) {
                sData['id'] = sId
            } else {
                sData['id'] = sId = UUID.randomUUID().toString().replace('-', '')
                log.info("New session '{}'", sId)
            }
        } else {// session的数据, 默认用ehcache 保存 session 数据
            if (ehcache == null) throw new RuntimeException('EhcacheSrv is not exist')
            def sData = (sId ? ehcache.get('session', sId) : null)
            if (sData == null) {
                sData = new ConcurrentHashMap()
                sData.id = sId = UUID.randomUUID().toString().replace('-', '')
                def cache = ehcache.getOrCreateCache('session', sessionExpire, Integer.valueOf(attrs()['session']['maxLimit']?:100000), null)
                cache.put(sId, sData)
                log.info("New session '{}'", sId)
            }
            ctx.metaClass['sData'] = sData
        }

        ctx.metaClass['sId'] = sId
        def c = ctx.response.cookie('sId', sId)
        c.maxAge = sessionExpire.plusSeconds(30).seconds
    }


    // 每小时统计请求的次数
    protected final Map<String, LongAdder> hourCount = new ConcurrentHashMap<>(3)
    // 请求统计
    protected count() {
        def sdf = new SimpleDateFormat('MM-dd HH')
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


    /**
     * https
     * @return
     */
    protected Tuple2<PrivateKey, X509Certificate> security() {
        SecureRandom random = new SecureRandom()
        def gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(2048, random)
        def pair = gen.genKeyPair()

        X509CertInfo info = new X509CertInfo()
        X500Name owner = new X500Name("C=x,ST=x,L=x,O=x,OU=x,CN=x")
        info.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3))
        info.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(new BigInteger(64, random)))
        try {
            info.set(X509CertInfo.SUBJECT, new CertificateSubjectName(owner))
        } catch (CertificateException ignore) {
            info.set(X509CertInfo.SUBJECT, owner)
        }
        try {
            info.set(X509CertInfo.ISSUER, new CertificateIssuerName(owner));
        } catch (CertificateException ignore) {
            info.set(X509CertInfo.ISSUER, owner);
        }
        info.set(X509CertInfo.VALIDITY, new CertificateValidity(
            new Date(System.currentTimeMillis() - 86400000L * 365),
            new Date(253402300799000L))
        )
        info.set(X509CertInfo.KEY, new CertificateX509Key(pair.getPublic()));
        info.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(new AlgorithmId(AlgorithmId.sha256WithRSAEncryption_oid)));

        // Sign the cert to identify the algorithm that's used.
        X509CertImpl cert = new X509CertImpl(info)
        cert.sign(pair.private, "SHA256withRSA");

        // Update the algorithm and sign again.
        info.set(CertificateAlgorithmId.NAME + '.' + CertificateAlgorithmId.ALGORITHM, cert.get(X509CertImpl.SIG_ALG));
        cert = new X509CertImpl(info);
        cert.sign(pair.private, "SHA256withRSA");
        cert.verify(pair.public)

        return Tuple.tuple(pair.private, cert)
    }


    @EL(name = ['http.hp', 'web.hp'], async = false)
    def getHp() {
        String ip = srv.bindHost
        if (ip == 'localhost') {ip = ipv4()}
        ip + ':' + srv.bindPort
    }


    @EL(name = 'web.sessionExpire', async = false)
    Duration getSessionExpire() {
        if (attrs()['session']['expire'] instanceof Duration) return attrs()['session']['expire']
        else if (attrs()['session']['expire'] instanceof Number || attrs()['session']['expire'] instanceof String) return Duration.ofMinutes(Long.valueOf(attrs()['session']['expire']))
        Duration.ofMinutes(30) // 默认session 30分钟
    }
}
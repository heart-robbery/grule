package core

import cn.xnatural.app.ServerTpl
import cn.xnatural.app.Utils
import cn.xnatural.enet.event.EL
import cn.xnatural.enet.event.EP
import cn.xnatural.sched.Sched
import com.alibaba.fastjson.JSON
import okhttp3.*

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiConsumer
import java.util.function.Consumer

import static java.util.Collections.emptyList

/**
 * 封装常用的http请求方式:
 * 1. get
 * 2. post json
 * 3. post form
 * 4. post multi form
 */
class OkHttpSrv extends ServerTpl {

    final Map<String, List<Cookie>> cookieStore = new ConcurrentHashMap<>()
    final Map<String, Set<String>> shareCookie = new ConcurrentHashMap<>()

    OkHttpSrv() {super('okHttp')}
    OkHttpSrv(String name) {super(name)}


    @EL(name = 'sys.starting', async = true)
    void init() {
        if (ep == null) {ep = new EP(); ep.addListenerSource(this)}
        exposeBean(client, name + "_client")
    }


    /**
     * OkHttpClient
     */
    @Lazy OkHttpClient client = {
        new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(getLong('connectTimeout', 5L)))
            .readTimeout(Duration.ofSeconds(getLong('readTimeout', 20L)))
            .writeTimeout(Duration.ofSeconds(getLong('writeTimeout', 32L)))
            .dispatcher(new Dispatcher(exec()))
            .cookieJar(new CookieJar() {// 共享cookie
                @Override
                void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                    def cs = cookieStore.get(url.host)
                    if (cs == null) {
                        cookieStore.put(url.host, new LinkedList<Cookie>(cookies)) // 可更改
                    } else {// 更新cookie
                        for (def itt = cs.iterator(); itt.hasNext(); ) {
                            Cookie coo = itt.next()
                            for (Cookie c: cookies) {
                                if (c.name() == coo.name()) {
                                    itt.remove()
                                    break
                                }
                            }
                        }
                        cs.addAll(cookies)
                    }
                }
                @Override
                List<Cookie> loadForRequest(HttpUrl url) {
                    List<Cookie> cookies = cookieStore.get(url.host)
                    if (shareCookie[(url.host)]) {
                        def rLs = new LinkedList<>((cookies?:emptyList()))
                        shareCookie[(url.host)].each {
                            rLs.addAll(cookieStore.get(it)?:emptyList())
                        }
                        return rLs
                    } else {
                        return cookies?:emptyList()
                    }
                }
            }).build()
    }()


    /**
     * get 请求
     * @param urlStr
     * @return
     */
    OkHttp get(String urlStr) {
        if (!urlStr) throw new IllegalArgumentException('url must not be empty')
        def b = new Request.Builder()
        def h = new OkHttp(urlStr, b)
        b.method = 'GET'
        h
    }


    /**
     * post 请求
     * @param urlStr
     * @return
     */
    OkHttp post(String urlStr) {
        if (!urlStr) throw new IllegalArgumentException('url must not be empty')
        def b = new Request.Builder()
        def h = new OkHttp(urlStr, b)
        b.method = 'POST'
        h
    }


    /**
     * 创建 WebSocket
     * @param urlStr 连接地址
     * @param tryInterval 重连接时间间隔. 秒
     * @param msgFn 消息处理函数
     */
    WebSocket ws(String urlStr, Integer tryInterval, BiConsumer<String, WebSocket> msgFn = null) {
        def req = new Request.Builder().url(urlStr).build()
        def listener = new WebSocketListener() {
            @Override
            void onOpen(WebSocket webSocket, Response resp) {
                log.info("WebSocket onOpen. url: " + urlStr)
            }
            @Override
            void onMessage(WebSocket webSocket, String msg) {
                msgFn?.accept(msg, webSocket)
            }
            @Override
            void onFailure(WebSocket ws, Throwable t, Response resp) {
                log.error("WS Error. url: " + urlStr + ", res: " + resp?.body()?.toString(), t)
                if (tryInterval) {
                    def sched = bean(Sched)
                    if (sched) {
                        sched.after(Duration.ofSeconds(tryInterval), { client.newWebSocket(req, this) })
                    } else {
                        log.warn("重连接WebSocket, 依赖于 sched 服务")
                    }
                }
            }
        }
        client.newWebSocket(req, listener)
    }


    class OkHttp {
        // 宿主
        protected final Request.Builder                      builder
        protected       String                               urlStr
        protected       Map<String, Object>                  params
        // 文件流:(属性名, 文件名, 文件内容)
        protected       List<Tuple3<String, String, Object>> fileStreams
        protected       Map<String, Object>                  cookies
        protected       String                               contentType
        protected       String                               bodyStr
        protected       boolean                              debug
        protected       Integer                              respCode

        protected OkHttp(String urlStr, Request.Builder builder) {
            if (builder == null) throw new NullPointerException('builder == null')
            this.builder = builder
            this.urlStr = urlStr
        }
        OkHttp param(String pName, Object pValue) {
            if (pName == null) throw new NullPointerException('pName == null')
            if (params == null) params = new LinkedHashMap<>()
            params.put(pName, pValue)
            if (pValue instanceof File) {
                if (contentType == null) contentType = 'multipart/form-data;charset=utf-8'
            }
            this
        }
        OkHttp fileStream(String pName, String fileName, Object body) {
            if (pName == null) throw new NullPointerException('pName == null')
            if (body == null) throw new NullPointerException('body == null')
            if (body instanceof byte[] || body instanceof String || body instanceof InputStream || body instanceof File) {
                if (fileStreams == null) fileStreams = new LinkedList<>()
                fileStreams.add(Tuple.tuple(pName, fileName, body))
                if (contentType == null) contentType = 'multipart/form-data;charset=utf-8'
            } else throw new IllegalArgumentException("Not support file body type: " + body.class.name)
            this
        }
        OkHttp jsonBody(String bodyStr) {
            this.bodyStr = bodyStr
            if (contentType == null) contentType = 'application/json;charset=utf-8'
            this
        }
        OkHttp textBody(String bodyStr) {
            this.bodyStr = bodyStr
            if (contentType == null) contentType = 'text/plain;charset=utf-8'
            this
        }
        OkHttp contentType(String contentType) {
            this.contentType = contentType
            this
        }
        OkHttp cookie(String name, Object value) {
            if (name == null) throw new NullPointerException('name == null')
            if (cookies == null) cookies = new HashMap<>(7)
            cookies.put(name, value)
            this
        }
        OkHttp header(String name, Object value) {
            if (value == null) throw new NullPointerException('value == null')
            builder.addHeader(name, value.toString())
            this
        }
        OkHttp debug() {this.debug = true; this}

        Integer getRespCode() { return respCode }
        // 请求执行
        def execute(Consumer<String> okFn = null, BiConsumer<Integer, Exception> failFn = {throw it}) {
            List<File> tmpFile = null
            if ('GET' == builder.method) { // get 请求拼装参数
                urlStr = Utils.buildUrl(urlStr, params)
                builder.get()
                // okHttp get 不能body
            }
            else if ('POST' == builder.method) {
                if (contentType &&
                    (contentType.containsIgnoreCase('application/json') ||
                        contentType.containsIgnoreCase('text/plain'))
                ) {
                    if (bodyStr) builder.post(RequestBody.create(MediaType.get(contentType), (bodyStr == null ? '' : bodyStr)))
                    else if (params) builder.post(RequestBody.create(MediaType.get(contentType), JSON.toJSONString(params)))
                } else if (contentType && contentType.containsIgnoreCase('multipart/form-data')) {
                    def b = new MultipartBody.Builder().setType(MediaType.get(contentType))
                    fileStreams?.each {
                        def v = it.v3
                        if (it.v3 instanceof InputStream) {
                            v = new File(getStr('tmpDir', Utils.baseDir('tmp').canonicalPath))
                            v.mkdirs()
                            v = new File(v,  "/" + UUID.randomUUID().toString().replace("-", ''))
                            v.append((InputStream) it.v3)
                            if (tmpFile == null) tmpFile = new LinkedList<>()
                            tmpFile.add(v)
                        }
                        b.addFormDataPart(it.v1, it.v2, RequestBody.create(MediaType.get('application/octet-stream'), v))
                    }
                    params?.each {
                        if (it.value instanceof File) {
                            b.addFormDataPart(it.key, ((File) it.value).name, RequestBody.create(MediaType.get('application/octet-stream'), (File) it.value))
                        } else {
                            b.addFormDataPart(it.key, (it.value == null ? '': it.value).toString())
                        }
                    }
                    builder.post(b.build())
                } else {
                    String bodyStr = params?.collect { it.key + '=' + URLEncoder.encode((it.value == null ? '': it.value).toString(), 'utf-8') + '&' }?.join('')?:''
                    if (bodyStr.endsWith('&') && bodyStr.length() > 2) bodyStr = bodyStr.substring(0, bodyStr.length() - 1)
                    builder.post(RequestBody.create(MediaType.get('application/x-www-form-urlencoded;charset=utf-8'), bodyStr))
                }
            }
            else throw new RuntimeException("not support http method '$builder.method'")

            // 删除url最后的&符号
            if (urlStr.endsWith('&') && urlStr.length() > 2) urlStr = urlStr.substring(0, urlStr.length() - 1)

            URI uri = URI.create(urlStr)
            if (getBoolean('enabledIntervalResolve', false)) {
                // 替换集群中的appName 对应的http地址,例: http://gy/test/cus, 找集群中对应gy的应用名所对应的http地址
                String hp = ep.fire("resolveHttp", uri.host)
                if (hp) {
                    String[] arr = hp.split(":")
                    uri.host = arr[0]
                    // 原始url中没有端口, 则设值
                    if (-1 == uri.port) uri.port = Integer.valueOf(arr[1])
                    uri.string = null
                    urlStr += ", hp: ${uri.host + ':' + uri.port}"
                }
            }

            HttpUrl url = HttpUrl.get(uri)
            // 添加cookie
            if (cookies) {
                def ls = cookieStore.computeIfAbsent(url.host(), {new LinkedList<Cookie>()})
                cookies.each { ls.add(Cookie.parse(url, "$it.key=$it.value")) }
            }

            def result
            def ex
            // 日志消息
            def logMsg = "Send http(${builder.method}): ${url.toString()}${ -> bodyStr ? ', body: ' + bodyStr : ''}${ -> params ? ', params: ' + params : ''}${ -> fileStreams ? "fileStreams: " + fileStreams.join(",") : ''}${ -> respCode == null ? '' : ' respCode: ' + respCode}${ -> result == null ? '' : ' result: ' + result}"
            try {
                // 发送请求
                def call =  client.newCall(builder.url(url).build())
                if (okFn) { // 异步请求
                    call.enqueue(new Callback() {
                        @Override
                        void onFailure(Call c, IOException e) {
                            log.error(logMsg.toString(), e)
                            tmpFile?.each {it.delete()}
                            failFn?.accept(respCode, e)
                        }

                        @Override
                        void onResponse(Call c, Response resp) throws IOException {
                            result = resp.body()?.string()
                            respCode = resp.code()
                            if (debug) log.info(logMsg.toString())
                            tmpFile?.each {it.delete()}
                            okFn?.accept(result)
                        }
                    })
                    null
                } else { // 同步请求
                    def resp = call.execute()
                    result = resp.body()?.string()
                    tmpFile?.each {it.delete()}
                    respCode = resp.code()
                }
            } catch(Exception t) {
                ex = t
            }
            if (debug && !okFn) {
                if (ex) {
                    log.error(logMsg.toString(), ex)
                } else {
                    log.info(logMsg.toString())
                }
            }
            if (ex) throw ex
            return result
        }
    }
}
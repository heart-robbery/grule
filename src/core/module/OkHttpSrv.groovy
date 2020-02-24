package core.module

import cn.xnatural.enet.event.EC
import cn.xnatural.enet.event.EL
import cn.xnatural.enet.event.EP
import com.alibaba.fastjson.JSON
import core.Utils
import okhttp3.*

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
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

    protected OkHttpClient client
    final Map<String, List<Cookie>> cookieStore = new ConcurrentHashMap<>()
    final Map<String, Set<String>> shareCookie = new ConcurrentHashMap<>()

    OkHttpSrv() {super('okHttp')}


    @EL(name = 'sys.starting')
    def init() {
        if (client) throw new RuntimeException("$name is already running")
        if (ep == null) {ep = new EP(); ep.addListenerSource(this)}
        client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(getLong('connectTimeout', 8)))
            .readTimeout(Duration.ofSeconds(getLong('readTimeout', 16)))
            .writeTimeout(Duration.ofSeconds(getLong('writeTimeout', 32)))
            .dispatcher(new Dispatcher(exec))
            .dns({String hostname ->
                try {
                    List<InetAddress> addrs = Dns.SYSTEM.lookup(hostname)
                    if (addrs) return addrs
                } catch(UnknownHostException ex) {}
                def addr = ep.fire("dns", new EC().async(false).args(hostname)) // 自定义dns 解析
                if (addr instanceof InetAddress) return [addr]
                else if (addr instanceof List) return addr
                throw new UnknownHostException("[$hostname]")
            })
            .cookieJar(new CookieJar() {// 共享cookie
                @Override
                void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                    def cs = cookieStore.get(url.host)
                    if (cs == null) {
                        cookieStore.put(url.host, new LinkedList<Cookie>(cookies)) // 可更改
                    } else {// 更新cookie
                        for (def it = cs.iterator(); it.hasNext(); ) {
                            Cookie coo = it.next()
                            for (Cookie c: cookies) {
                                if (c.name() == coo.name()) {
                                    it.remove()
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
        exposeBean(client)
    }


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


    OkHttpClient client() {client}

    class OkHttp {
        // 宿主
        protected final Request.Builder                           builder
        protected       String                                    urlStr
        protected       Map<String, Object>                       params
        // 文件流:(属性名, 文件名, 文件流)
        protected       List<Tuple3<String, String, InputStream>> fileStreams
        protected       Map<String, Object>                       cookies
        protected       String                                    contentType
        protected       String                                    jsonBodyStr
        protected       boolean                                   debug

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
        OkHttp fileStream(String pName, String fileName, InputStream is) {
            if (pName == null) throw new NullPointerException('pName == null')
            if (is == null) throw new NullPointerException('InputStream == null')
            if (fileStreams == null) fileStreams = new LinkedList<>()
            fileStreams.add(Tuple.tuple(pName, fileName, is))
            if (contentType == null) contentType = 'multipart/form-data;charset=utf-8'
            this
        }
        OkHttp jsonBody(String jsonBodyStr) {
            this.jsonBodyStr = jsonBodyStr
            if (contentType == null) contentType = 'application/json;charset=utf-8'
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
        // 请求执行
        def execute(Consumer<String> okFn = null, Consumer<Exception> failFn = {throw it}) {
            if ('GET' == builder.method) { // get 请求拼装参数
                urlStr = Utils.buildUrl(urlStr, params)
                builder.get()
            } else if ('POST' == builder.method) {
                if (contentType && contentType.containsIgnoreCase('application/json')) {
                    if (jsonBodyStr) builder.post(RequestBody.create(MediaType.get(contentType), (jsonBodyStr == null ? '' : jsonBodyStr)))
                    else if (params) builder.post(RequestBody.create(MediaType.get(contentType), JSON.toJSONString(params)))
                } else if (contentType && contentType.containsIgnoreCase('multipart/form-data')) {
                    def b = new MultipartBody.Builder().setType(MediaType.get(contentType))
                    fileStreams?.each {
                        b.addFormDataPart(it.v1, it.v2, RequestBody.create(MediaType.get('application/octet-stream'), it.v3))
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
            } else throw new RuntimeException("not support http method '$builder.method'")

            // 删除url最后的&符号
            if (urlStr.endsWith('&') && urlStr.length() > 2) urlStr = urlStr.substring(0, urlStr.length() - 1)

            // 替换集群中的appName 对应的http地址,例: http://gy/test/cus, 当gy用dns 解析不出来的时候, 就会找集群中对应gy的应用名所对应的http地址
            URI uri = URI.create(urlStr)
            def addrs
            try {
                addrs = Dns.SYSTEM.lookup(uri.host)
            } catch (UnknownHostException t) {}
            if (!addrs) {
                String hp = ep.fire("resolveHttp", uri.host)
                if (hp) {
                    String[] arr = hp.split(":")
                    uri.host = arr[0]
                    // 原始url中没有端口, 则设值
                    if (-1 == uri.port) uri.port = Integer.valueOf(arr[1])
                    uri.string = null
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
            try {
                // 发送请求
                def call =  client.newCall(builder.url(url).build())
                if (okFn) { // 异步请求
                    call.enqueue(new Callback() {
                        @Override
                        void onFailure(Call c, IOException e) {
                            if (debug) log.error('Send http: {}, params: {}', urlStr, params?:jsonBodyStr)
                            failFn?.accept(e)
                        }

                        @Override
                        void onResponse(Call c, Response resp) throws IOException {
                            result = resp.body()?.string()
                            if (200 != resp.code()) {
                                log.error('Send http: {}, params: {}, result: ' + Objects.toString(result, ''), urlStr, params?:jsonBodyStr)
                                if (failFn) {
                                    failFn.accept(new RuntimeException("Http error. code: ${resp.code()}, url: $urlStr, resp: ${Objects.toString(result, '')}"))
                                }
                            } else {
                                log.info('Send http: {}, params: {}, result: ' + Objects.toString(result, ''), urlStr, params?:jsonBodyStr)
                                okFn?.accept(result)
                            }
                        }
                    })
                    null
                } else { // 同步请求
                    def resp = call.execute()
                    result = resp.body()?.string()
                    if (200 != resp.code()) {
                        throw new RuntimeException("Http error. code: ${resp.code()}, url: $urlStr, resp: ${Objects.toString(result, '')}")
                    }
                }
            } catch(Throwable t) {
                ex = t
            }
            if (debug) {
                if (ex) {
                    log.error('Send http: {}, params: {}', urlStr, params?:jsonBodyStr)
                } else {
                    log.info('Send http: {}, params: {}, result: ' + Objects.toString(result, ''), urlStr, params?:jsonBodyStr)
                }
            }
            if (ex) throw ex
            return result
        }
    }
}
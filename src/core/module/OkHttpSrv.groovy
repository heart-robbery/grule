package core.module

import cn.xnatural.enet.event.EL
import cn.xnatural.enet.event.EP
import com.alibaba.fastjson.JSON
import okhttp3.*

import javax.annotation.Resource
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
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

    @Resource ExecutorService exec
    protected OkHttpClient client
    protected final Map<String, List<Cookie>> cookieStore = new ConcurrentHashMap<>()


    @EL(name = 'sys.starting')
    def init() {
        if (client) throw new RuntimeException("$name is already running")
        if (ep == null) {ep = new EP(); ep.addListenerSource(this)}
        client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(Long.valueOf(attrs.connectTimeout?:8)))
                .readTimeout(Duration.ofSeconds(Long.valueOf(attrs.readTimeout?:16)))
                .writeTimeout(Duration.ofSeconds(Long.valueOf(attrs.writeTimeout?:30)))
                .dispatcher(new Dispatcher(exec))
                .cookieJar(new CookieJar() {// 共享cookie
                    @Override
                    void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                        cookieStore.put("$url.host:$url.port", new ArrayList<Cookie>(cookies)) // 可更改
                    }
                    @Override
                    List<Cookie> loadForRequest(HttpUrl url) {
                        List<Cookie> cookies = cookieStore.get("$url.host:$url.port")
                        return cookies != null ? cookies : emptyList()
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
        def h = new OkHttp(this, urlStr, b)
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
        def h = new OkHttp(this, urlStr, b)
        b.method = 'POST'
        h
    }


    OkHttpClient client() {client}


    class OkHttp {
        // 宿主
        protected final OkHttpSrv                                 parent
        protected final Request.Builder                           builder
        protected       String                                    urlStr
        protected       Map<String, Object>                       params
        // 文件流:(属性名, 文件名, 文件流)
        protected       List<Tuple3<String, String, InputStream>> fileStreams
        protected       Map<String, Object>                       cookies
        protected       String                                    contentType
        protected       String                                    jsonBodyStr

        protected OkHttp(OkHttpSrv parent, String urlStr, Request.Builder builder) {
            if (builder == null) throw new NullPointerException('builder == null')
            if (parent == null) throw new NullPointerException('parasitifer == null')
            this.builder = builder
            this.parent = parent
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
        // 请求执行
        def execute(Consumer<String> okFn = null, Consumer<Exception> failFn = {throw it}) {
            if ('GET' == builder.method) { // get 请求拼装参数
                params?.each {
                    if (urlStr.endsWith('?')) urlStr += (it.key + '=' + (it.value == null ? '' : it.value).toString() + '&')
                    else if (urlStr.endsWith('&')) urlStr += (it.key + '=' + (it.value == null ? '': it.value).toString() + '&')
                    else urlStr += ('?' + it.key + '=' + (it.value == null ? '': it.value).toString() + '&')
                }
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

            HttpUrl url = HttpUrl.get(urlStr)
            // 添加cookie
            if (cookies) {
                def ls = cookieStore.computeIfAbsent("$url.host:$url.port", {new ArrayList<Cookie>(7)})
                cookies.each { ls.add(Cookie.parse(url, "$it.key=$it.value")) }
            }

            parent.log.info('Send http: {}, params: {}', urlStr, params?:jsonBodyStr)
            // 发送请求
            def call =  parent.client.newCall(builder.url(url).build())
            if (okFn) { // 异步请求
                call.enqueue(new Callback() {
                    @Override
                    void onFailure(Call c, IOException e) {failFn?.accept(e) }

                    @Override
                    void onResponse(Call c, Response resp) throws IOException {
                        okFn?.accept(resp.body().string())
                    }
                })
            } else { // 同步请求
                def resp = call.execute()
                if (200 != resp.code()) throw new RuntimeException("Http error code: ${resp.code()}, url: $urlStr")
                resp.body().string()
            }
        }
    }
}

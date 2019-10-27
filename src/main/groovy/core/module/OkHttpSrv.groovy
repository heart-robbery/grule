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
    def create() {
        if (client) throw new RuntimeException("$name is already running")
        if (ep == null) {ep = new EP(); ep.addListenerSource(this)}
        client = new OkHttpClient.Builder()
                .connectTimeout(attrs.connectTimeout?:Duration.ofSeconds(5))
                .readTimeout(attrs.readTimeout?:Duration.ofSeconds(15))
                .dispatcher(new Dispatcher(exec))
                .cookieJar(new CookieJar() {// 共享cookie
                    @Override
                    void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                        cookieStore.put("$url.host:$url.port", cookies)
                    }
                    @Override
                    List<Cookie> loadForRequest(HttpUrl url) {
                        List<Cookie> cookies = cookieStore.get("$url.host:$url.port")
                        return cookies != null ? cookies : new ArrayList<>(7)
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
        def b = new Request.Builder().get()
        def h = new OkHttp(this, b)
        h.urlStr = urlStr
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
        def h = new OkHttp(this, b)
        h.urlStr = urlStr
        b.method = 'POST'
        h
    }


    OkHttpClient client() {client}


    class OkHttp {
        // 宿主
        protected final OkHttpSrv parasitifer
        protected final Request.Builder builder
        protected String urlStr
        protected Map<String, Object> params
        protected Map<String, Object> cookies
        protected String contentType
        protected String jsonBodyStr

        OkHttp(OkHttpSrv parasitifer, Request.Builder builder) {
            if (builder == null) throw new NullPointerException('builder == null')
            if (parasitifer == null) throw new NullPointerException('parasitifer == null')
            this.builder = builder
            this.parasitifer = parasitifer
        }
        OkHttp param(String pName, Object pValue) {
            if (params == null) params = new LinkedHashMap<>()
            params.put(pName, pValue)
            if (pValue instanceof File) {
                if (contentType == null) contentType = 'multipart/form-data;charset=utf-8'
            }
            this
        }
        OkHttp jsonBody(String jsonBodyStr) {
            this.jsonBodyStr = jsonBodyStr
            if (contentType == null) contentType = 'application/json;charset=utf-8'
            this
        }
        OkHttp cookie(String name, Object value) {
            if (cookies == null) cookies = new HashMap<>(7)
            cookies.put(name, value)
            this
        }
        OkHttp header(String name, Object value) {
            builder.addHeader(name, value)
            this
        }
        def execute(Consumer<String> okFn = null, Consumer<Exception> failFn = {throw it}) {
            if ('GET' == builder.method) { // get 请求拼装参数
                params?.each {
                    if (it.key == null) return
                    if (urlStr.endsWith('?')) urlStr += (it.key + '=' + it.value + '&')
                    else if (urlStr.endsWith('&')) urlStr += (it.key + '=' + it.value + '&')
                    else urlStr += ('?' + it.key + '=' + it.value + '&')
                }
            } else if ('POST' == builder.method) {
                if (contentType && contentType.containsIgnoreCase('application/json')) {
                    if (jsonBodyStr) builder.post(RequestBody.create(MediaType.get(contentType), (jsonBodyStr == null ? '' : jsonBodyStr)))
                    else if (params) builder.post(RequestBody.create(MediaType.get(contentType), JSON.toJSONString(params)))
                } else if (contentType && contentType.containsIgnoreCase('multipart/form-data')) {

                } else {
                    String bodyStr = params?.collect { it.key + '=' + it.value + '&' }?.join('')?:''
                    if (bodyStr.endsWith('&') && bodyStr.length() > 2) bodyStr = bodyStr.substring(0, bodyStr.length() - 1)
                    builder.post(RequestBody.create(MediaType.get('application/x-www-form-urlencoded;charset=utf-8'), bodyStr))
                }
            } else throw new RuntimeException("not support http method '$builder.method'")

            if (urlStr.endsWith('&') && urlStr.length() > 2) urlStr = urlStr.substring(0, urlStr.length() - 1)

            HttpUrl url = HttpUrl.get(urlStr)
            // 添加cookie
            if (cookies) {
                def ls = cookieStore.computeIfAbsent("$url.host:$url.port", {new ArrayList<Cookie>(7)})
                cookies.each { ls.add(Cookie.parse(url, "$it.key=$it.value")) }
            }

            parasitifer.log.info('Send http: {}, params: {}', urlStr, params?:jsonBodyStr)
            def call =  parasitifer.client.newCall(builder.url(url).build())
            if (okFn) {
                call.enqueue(new Callback() {
                    @Override
                    void onFailure(Call c, IOException e) {failFn?.accept(e) }

                    @Override
                    void onResponse(Call c, Response resp) throws IOException {
                        okFn?.accept(resp.body().string())
                    }
                })
            } else {
                def resp = call.execute()
                if (200 != resp.code()) throw new RuntimeException("Http error code: ${resp.code()}, url: $urlStr")
                resp.body().string()
            }
        }
    }
}

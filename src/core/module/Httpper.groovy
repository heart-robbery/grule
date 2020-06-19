package core.module

import cn.xnatural.enet.event.EL
import okhttp3.Request
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.client.methods.RequestBuilder
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder

class Httpper extends ServerTpl {

    protected CloseableHttpClient hc

    @EL(name = 'sys.starting', async = false)
    void start() {
        hc = HttpClientBuilder.create()
            .setSSLHostnameVerifier({true})
            .setDefaultRequestConfig(RequestConfig.custom()
                .setConnectTimeout(getInteger("connectTimeout", 5000))
                .setSocketTimeout(getInteger("socketTimeout", 20000))
                .setConnectionRequestTimeout(5000)
                .build())
            .build()
    }


    @EL(name = 'sys.stopping')
    void stop() {
        hc?.close()
    }


    Httpp get(String urlStr) {
        if (!urlStr) throw new IllegalArgumentException('url must not be empty')

    }


    class Httpp {
        protected       String                                    urlStr
        protected       Map<String, Object>                       params
        // 文件流:(属性名, 文件名, 文件内容)
        protected       List<Tuple3<String, String, Object>>      fileStreams
        protected       Map<String, Object>                       cookies
        protected       String                                    contentType
        protected       String                                    jsonBodyStr
        protected       boolean                                   debug

        protected Httpp(String urlStr, Request.Builder builder) {
            if (builder == null) throw new NullPointerException('builder == null')
            this.builder = builder
            this.urlStr = urlStr
        }
        Httpp param(String pName, Object pValue) {
            if (pName == null) throw new NullPointerException('pName == null')
            if (params == null) params = new LinkedHashMap<>()
            params.put(pName, pValue)
            if (pValue instanceof File) {
                if (contentType == null) contentType = 'multipart/form-data;charset=utf-8'
            }
            this
        }
        Httpp fileStream(String pName, String fileName, Object body) {
            if (pName == null) throw new NullPointerException('pName == null')
            if (body == null) throw new NullPointerException('body == null')
            if (body instanceof byte[] || body instanceof String || body instanceof InputStream || body instanceof File) {
                if (fileStreams == null) fileStreams = new LinkedList<>()
                fileStreams.add(Tuple.tuple(pName, fileName, body))
                if (contentType == null) contentType = 'multipart/form-data;charset=utf-8'
            } else throw new IllegalArgumentException("Not support file body type: " + body.class.name)
            this
        }
        Httpp jsonBody(String jsonBodyStr) {
            this.jsonBodyStr = jsonBodyStr
            if (contentType == null) contentType = 'application/json;charset=utf-8'
            this
        }
        Httpp contentType(String contentType) {
            this.contentType = contentType
            this
        }
        Httpp cookie(String name, Object value) {
            if (name == null) throw new NullPointerException('name == null')
            if (cookies == null) cookies = new HashMap<>(7)
            cookies.put(name, value)
            this
        }
        Httpp header(String name, Object value) {
            if (value == null) throw new NullPointerException('value == null')
            builder.addHeader(name, value.toString())
            this
        }
        Httpp debug() {this.debug = true; this}

        String execute() {
            RequestBuilder.get("").build()
            hc.execute()
        }
    }
}

package core.mode.http

import java.util.function.Consumer

class Httper {
    protected       String                                    urlStr
    protected       String                                    method
    protected       Map<String, Object>                       params
    // 文件流:(属性名, 文件名, 文件流)
    protected       List<Tuple3<String, String, InputStream>> fileStreams
    protected       Map<String, Object>                       cookies
    protected       Map<String, String>                       headers
    protected       String                                    contentType
    protected       String                                    jsonBodyStr
    protected       boolean                                   print
    // 请求执行函数
    protected       Runnable                                  doRequestFn
    protected       Consumer<String> okFn
    protected       Consumer<Throwable> failFn


    static Httper get(String url) { new Httper(urlStr: url, method: 'GET') }


    static Httper post(String url) { new Httper(urlStr: url, method: 'POST') }


    // 请求执行
    def execute(Consumer<String> okFn = null, Consumer<Throwable> failFn = {throw it}) {
        this.okFn = okFn
        this.failFn = failFn
        doRequestFn.run()
    }


    Httper param(String pName, Object pValue) {
        if (pName == null) throw new NullPointerException('pName == null')
        if (params == null) params = new LinkedHashMap<>()
        params.put(pName, pValue)
        if (pValue instanceof File) {
            if (contentType == null) contentType = 'multipart/form-data;charset=utf-8'
        }
        this
    }


    Httper fileStream(String pName, String fileName, InputStream is) {
        if (pName == null) throw new NullPointerException('pName == null')
        if (is == null) throw new NullPointerException('InputStream == null')
        if (fileStreams == null) fileStreams = new LinkedList<>()
        fileStreams.add(Tuple.tuple(pName, fileName, is))
        if (contentType == null) contentType = 'multipart/form-data;charset=utf-8'
        this
    }


    Httper jsonBody(String jsonBodyStr) {
        this.jsonBodyStr = jsonBodyStr
        if (contentType == null) contentType = 'application/json;charset=utf-8'
        this
    }


    Httper contentType(String contentType) {
        this.contentType = contentType
        this
    }


    Httper cookie(String name, Object value) {
        if (name == null) throw new NullPointerException('name == null')
        if (cookies == null) cookies = new HashMap<>(7)
        cookies.put(name, value)
        this
    }


    Httper header(String name, Object value) {
        if (value == null) throw new NullPointerException('value == null')
        if (headers == null) headers = new LinkedHashMap<>()
        headers.put(name, value.toString())
        this
    }


    Httper print() {this.print = true; this}
}

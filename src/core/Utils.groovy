package core

import com.alibaba.fastjson.JSON
import org.codehaus.groovy.runtime.InvokerHelper
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.lang.management.ManagementFactory
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.function.Function

class Utils {
    static Logger log = LoggerFactory.getLogger(Utils)

    static final GroovyClassLoader  gcl = new GroovyClassLoader()

    /**
     * 得到jvm进程号
     * @return
     */
    static String pid() { ManagementFactory.getRuntimeMXBean().getName().split("@")[0] }


    /**
     * 判断系统是否为 linux 系统
     * 判断方法来源 {@link io.netty.channel.epoll.Native#loadNativeLibrary()}
     * @return
     */
    static boolean isLinux() {
        System.getProperty("os.name").toLowerCase(Locale.UK).trim().startsWith("linux")
    }


    /**
     * 查找方法
     * @param clz
     * @param mName
     * @param parameterTypes
     * @return
     */
    static Method findMethod(final Class clz, String mName, Class<?>... parameterTypes) {
        Class c = clz
        do {
            Method m = null
            try {
                m = c.getDeclaredMethod(mName, parameterTypes)
            } catch (NoSuchMethodException e) { }
            if (m) return m
            c = c.getSuperclass()
        } while (c)
        return null
    }


    /**
     * 遍历所有方法并处理
     * @param clz
     * @param fn
     */
    static void iterateMethod(final Class clz, Consumer<Method> fn) {
        if (fn == null) return
        Class c = clz
        do {
            for (Method m : c.getDeclaredMethods()) fn.accept(m)
            c = c.getSuperclass()
        } while (c)
    }


    /**
     * 查找字段
     * @param clz
     * @param fn
     */
    static void iterateField(final Class clz, Consumer<Field> fn) {
        if (fn == null) return
        Class c = clz
        do {
            for (Field f : c.getDeclaredFields()) fn.accept(f)
            c = c.getSuperclass()
        } while (c != null)
    }


    /**
     * 构建一个 http 请求, 支持 get, post. 文件上传.
     * @return
     */
    static Http http() { return new Http() }


    static class Http {
        private String                      urlStr
        private String                      contentType
        private String                      method
        private String                      jsonBody
        private Map<String, Object>         params
        private Map<String, Object>         cookies
        private Map<String, String>         headers
        private int                         connectTimeout = 5000
        private int                         readTimeout = 15000
        private int respCode
        private Consumer<HttpURLConnection> preFn

        Http get(String url) { this.urlStr = url; this.method = 'GET'; return this }
        Http post(String url) { this.urlStr = url; this.method = 'POST'; return this }
        /**
         *  设置 content-type
         * @param contentType application/json, multipart/form-data, application/x-www-form-urlencoded
         * @return
         */
        Http contentType(String contentType) { this.contentType = contentType; return this }
        Http jsonBody(String jsonStr) {this.jsonBody = jsonStr; if (contentType == null) contentType = 'application/json'; return this }
        Http readTimeout(int timeout) { this.readTimeout = timeout; return this }
        Http connectTimeout(int timeout) { this.connectTimeout = timeout; return this }
        Http preConnect(Consumer<HttpURLConnection> preConnect) { this.preFn = preConnect; return this }
        /**
         * 添加参数
         * @param name 参数名
         * @param value 支持 {@link File}
         * @return
         */
        Http param(String name, Object value) {
            if (params == null) params = new LinkedHashMap<>()
            params.put(name, value)
            return this
        }
        Http header(String name, String value) {
            if (headers == null) headers = new HashMap<>(7)
            headers.put(name, value)
            return this
        }
        Http cookie(String name, Object value) {
            if (cookies == null) cookies = new HashMap<>(7)
            cookies.put(name, value)
            return this
        }
        Map<String, Object> cookies() {return cookies}
        int getResponseCode() {return respCode}

        /**
         * 执行 http 请求
         * @return http请求结果
         */
        String execute() {
            String ret
            HttpURLConnection conn
            boolean isMulti = false // 是否为 multipart/form-data 提交
            try {
                URL url = null
                if (!urlStr) throw new IllegalArgumentException('url不能为空')
                if ('GET' == method) url = new URL(buildUrl(urlStr, params))
                else if ('POST' == method) url = new URL(urlStr)
                conn = (HttpURLConnection) url.openConnection()
                if (conn instanceof HttpsURLConnection) { // 如果是https, 就忽略验证
                    SSLContext sc = SSLContext.getInstance('TLSv1.2') // "TLS"
                    sc.init(null, new TrustManager[] {new X509TrustManager() {
                        @Override
                        void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException { }
                        @Override
                        void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException { }
                        @Override
                        X509Certificate[] getAcceptedIssuers() { return null }
                    }}, new java.security.SecureRandom())
                    ((HttpsURLConnection) conn).setHostnameVerifier({s, sslSession -> true})
                    ((HttpsURLConnection) conn).setSSLSocketFactory(sc.getSocketFactory())
                }
                conn.setRequestMethod(method)
                conn.setConnectTimeout(connectTimeout)
                conn.setReadTimeout(readTimeout)

                // header 设置
                conn.setRequestProperty('Accept', '*/*') // 必加
                conn.setRequestProperty('Charset', 'UTF-8')
                conn.setRequestProperty('Accept-Charset', 'UTF-8')
                if (contentType) conn.setRequestProperty('Content-Type', "$contentType;charset=UTF-8")
                // conn.setRequestProperty("Connection", "close")
                // conn.setRequestProperty("Connection", "keep-alive")
                // conn.setRequestProperty("http_user_agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/63.0.3239.26 Safari/537.36 Core/1.63.6726.400 QQBrowser/10.2.2265.400")
                headers?.each {conn.setRequestProperty(it.key, it.value)}

                String boundary = null
                if ('POST' == method) {
                    conn.setUseCaches(false)
                    conn.setDoOutput(true)
                    if ('multipart/form-data' == contentType || (params?.find{it.value instanceof File}) != null) {
                        boundary = "----CustomFormBoundary${UUID.randomUUID().toString()}"
                        contentType = "multipart/form-data;boundary=$boundary"
                        isMulti = true
                    }
                }

                // cookie 设置
                if (cookies) {
                    StringBuilder sb = new StringBuilder()
                    cookies.each {
                        if (it.value != null) {
                            sb.append(it.key).append("=").append(it.value).append(";")
                        }
                    }
                    conn.setRequestProperty('Cookie', sb.toString())
                }

                preFn?.accept(conn)
                conn.connect()  // 连接

                if ('POST' == method) {
                    DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                    if ('application/json' == contentType && (params || jsonBody)) {
                        if (jsonBody == null) os.write(JSON.toJSONString(params).getBytes())
                        else os.write(jsonBody.getBytes('utf-8'))
                        os.flush(); os.close()
                    } else if (isMulti && params) {
                        String end = "\r\n"
                        String twoHyphens = "--"
                        params.each {e ->
                            os.writeBytes(twoHyphens + boundary + end)
                            if (e.value instanceof File) {
                                String s = "Content-Disposition: form-data; name=\"$e.key\"; filename=\"${((File) e.value).name}\"" + end
                                os.write(s.getBytes('utf-8')) // 这样写是为了避免中文文件名乱码
                                os.writeBytes(end)
                                // copy
                                def is = new FileInputStream(File)
                                def bs = new byte[4028]
                                int n
                                while (-1 != (n = is.read(bs))) {os.write(bs, 0, n)}
                            } else if (e.value instanceof String) {
                                os.write(("Content-Disposition: form-data; name=\"$e.key\"" + end).getBytes("utf-8"))
                                os.writeBytes(end)
                                os.write(e.value.toString().getBytes('utf-8'))
                            } else throw new IllegalArgumentException('not support parameter')
                            os.writeBytes(end)
                        }
                        os.writeBytes(twoHyphens + boundary + twoHyphens + end)
                        os.flush(); os.close()
                    } else if (params) {
                        StringBuilder sb = new StringBuilder()
                        params.each {
                            if (it.value != null) {
                                sb.append(it.key + "=" + URLEncoder.encode(it.value.toString(), 'utf-8') + "&")
                            }
                        }
                        os.write(sb.toString().getBytes('utf-8'))
                        os.flush(); os.close()
                    }
                }
                // http 状态码
                respCode = conn.getResponseCode()
                // 保存cookie
                conn.getHeaderFields().get('Set-Cookie')?.each {c ->
                    String[] arr = c.split(";")[0].split("=")
                    cookie(arr[0], arr[1])
                }

                // 取结果
                ret = conn.getInputStream().getText('utf-8')
            } finally {
                conn?.disconnect()
            }
            return ret
        }
    }


    /**
     * 把查询参数添加到 url 后边
     * @param urlStr
     * @param params
     * @return
     */
    static String buildUrl(String urlStr, Map<String, Object> params) {
        if (!params) return urlStr
        StringBuilder sb = new StringBuilder(urlStr)
        if (!urlStr.endsWith("?")) sb.append("?")
        params.each { sb.append(it.key).append("=").append(URLEncoder.encode(it.value.toString(), 'utf-8')).append("&") }
        return sb.toString()
    }


    // 脚本类缓存
    static final Map<String, Script> scriptCache = new ConcurrentHashMap<>()
    /**
     * 执行一段groovy脚本
     * @param scriptText
     * @param ctx
     * @return
     */
    static Object eval(String scriptText, Map ctx = new HashMap()) {
        Script script = scriptCache.computeIfAbsent(scriptText {InvokerHelper.createScript(gcl.parseClass(scriptText), new Binding(ctx))})
        return script.run()
    }


    /**
     * 文件内容监控器(类linux tail)
     * @return
     */
    static Tailer tailer() {new Tailer()}

    static class Tailer {
        private Thread                    th
        private boolean                   stopFlag
        private Function<String, Boolean> lineFn

        // 处理输出行
        Tailer handle(Function<String, Boolean> lineFn) {this.lineFn = lineFn; this}

        def stop() {this.stopFlag = true}

        /**
         * tail 文件内容监控
         * @param file 文件全路径
         * @param follow 从最后第几行开始
         * @return
         */
        Tailer tail(String file, Integer follow = 10) {
            th = new Thread({run(file, (follow == null ? 0 : follow))}, "Tailer-$file")
            th.setDaemon(true)
            th.start()
            this
        }

        private def run(String file, Integer follow) {
            if (lineFn == null) throw new IllegalArgumentException("没有行处理函数")
            RandomAccessFile raf = new RandomAccessFile(file, "r")
            Queue<String> buffer = new LinkedList<>() // 用来保存最后几行(follow)数据

            // 当前第一次到达文件结尾时,设为true
            boolean firstEnd
            String line
            while (!stopFlag) {
                try {
                    line = raf.readLine()
                    if (line == null) { // 当读到文件结尾时
                        if (firstEnd) Thread.sleep(1000L)
                        else { // 第一次到达结尾后, 清理buffer数据
                            firstEnd = true
                            do {
                                line = buffer.poll()
                                if (line) stopFlag = lineFn.apply(line)
                            } while (line)
                            buffer = null
                        }
                    } else {
                        if (firstEnd) { // 直接处理行字符串
                            stopFlag = lineFn.apply(line)
                            Thread.sleep(200L)
                        } else {
                            if (follow > 0) {
                                buffer.offer(line)
                                if (buffer.size() >= follow) buffer.poll()
                            }
                        }
                    }
                } catch (Throwable ex) {
                    stopFlag = true
                    log.error("tail file '" + file + "' error", ex)
                }
            }
            raf?.close()
        }
    }
}

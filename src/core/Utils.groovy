package core

import com.alibaba.fastjson.JSON
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.lang.management.ManagementFactory
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.Executor
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate

class Utils {
    protected static final Logger log = LoggerFactory.getLogger(Utils)
    // static final GroovyClassLoader gcl = new GroovyClassLoader()


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
     * 项目根目录下边找目录或文件
     * @param child 项目根目录下的子目录/文件
     * @return
     */
    static File baseDir(String child = null) {
        def p = new File(System.getProperty('user.dir')).parentFile
        if (child) {return new File(p, child)}
        return p
    }


    /**
     * 本机ipv4地址
     * @return
     */
    static String ipv4() {
        for (def en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
            NetworkInterface current = en.nextElement()
            if (!current.isUp() || current.isLoopback() || current.isVirtual()) continue
            Enumeration<InetAddress> addresses = current.getInetAddresses()
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement()
                if (addr.isLoopbackAddress()) continue
                if (addr instanceof Inet4Address) {
                    return addr.getHostAddress()
                }
            }
        }
        null
    }


    /**
     * sha1 加密
     * @param str
     * @return
     */
    static byte[] sha1(byte[] bs) {
        MessageDigest digest = java.security.MessageDigest.getInstance('SHA-1')
        digest.update(bs)
        return digest.digest()
    }


//    String md5Hex(String str) {
//        def md = MessageDigest.getInstance('MD5')
//        def bs = md.update(str.getBytes('utf-8'))
//
//    }


    static final char[] CS = ['0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z','A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z']
    /**
     * 随机字符串(区分大小写)
     * @param length 长度
     * @param prefix 前缀
     * @param suffix 后缀
     */
    static String random(int length, String prefix = null, String suffix = null) {
        if (length < 1) throw new IllegalArgumentException("length must le 1")
        final cs = new char[length]
        def r = new Random()
        for (int i = 0; i < cs.length; i++) {
            cs[i] = CS[r.nextInt(CS.length)]
        }
        return (prefix?:'') + String.valueOf(cs) + (suffix?:'')
    }


    /**
     * 类型转换
     * @param v
     * @param type
     * @return
     */
    static <T> T to(Object v, Class<T> type) {
        if (type == null) return v
        if (v == null) return type.cast(v)
        else if (type == String) return v.toString()
        else if (type == Boolean || type == boolean) return v as Boolean
        else if (type == Integer || type == int) return Integer.valueOf(v.toString())
        else if (type == Long || type == long) return Long.valueOf(v.toString())
        else if (type == Double || type == double) return Double.valueOf(v.toString())
        else if (type == Float || type == float) return Float.valueOf(v.toString())
        else if (type == BigDecimal) return new BigDecimal(v.toString())
        else if (type == URI) return URI.create(v.toString())
        else if (type == URL) return URI.create(v.toString()).toURL()
        else return v
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
        private int                         respCode
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
                conn.setRequestProperty("Accept", "*/*") // 必加
                conn.setRequestProperty("Charset", "UTF-8")
                conn.setRequestProperty("Accept-Charset", "UTF-8")
                if (contentType) conn.setRequestProperty("Content-Type", "$contentType;charset=UTF-8")
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
                    DataOutputStream os = new DataOutputStream(conn.getOutputStream())
                    if ('application/json' == contentType && (params || jsonBody)) {
                        if (jsonBody == null) os.write(JSON.toJSONString(params).getBytes())
                        else os.write(jsonBody.getBytes("utf-8"))
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
                            } else throw new IllegalArgumentException("not support parameter")
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
                ret = conn.getInputStream().getText("utf-8")
                if (200 != responseCode) {
                    throw new Exception("Http error. code: ${responseCode}, url: $urlStr, resp: ${Objects.toString(ret, "")}")
                }
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
        params.each {
            if (it.value != null) {
                def v = URLEncoder.encode(it.value, 'utf-8')
                if (urlStr.endsWith('?')) urlStr += (it.key + '=' + v + '&')
                else if (urlStr.endsWith('&')) urlStr += (it.key + '=' + v + '&')
                else if (urlStr.contains("?")) urlStr += ("&" + it.key + '=' + v + '&')
                else urlStr += ('?' + it.key + '=' + v + '&')
            }
        }
        return urlStr
    }


//    // 脚本类缓存
//    protected static final Map<String, Script> scriptCache  = new ConcurrentHashMap<>()
//    // 类名计数
//    protected static final AtomicInteger       clzNameCount = new AtomicInteger(0)
//    /**
//     * 执行一段groovy脚本
//     * 避免OOM: java.lang.ClassLoader#getClassLoadingLock (只增不减的map)
//     * @param scriptText
//     * @param ctx
//     * @return
//     */
//    static Object eval(String scriptText, Map ctx = new LinkedHashMap()) {
//        if (scriptText == null || scriptText.isEmpty()) throw new IllegalArgumentException("scriptText must not be empty")
//        scriptCache.computeIfAbsent(
//            scriptText,
//            {InvokerHelper.createScript(gcl.parseClass(scriptText, "GroovyDynClz_${clzNameCount.getAndIncrement()}"), new Binding(ctx))}
//        ).run()
//    }


    /**
     * 文件内容监控器(类linux tail)
     * @return
     */
    static Tailer tailer() {new Tailer()}

    static class Tailer {
        private Thread                    th
        private boolean                   stopFlag
        private Function<String, Boolean> lineFn
        private Executor exec

        /**
         * 处理输出行
         * @param lineFn 函数返回true 继续输出, 返回false则停止输出
         */
        Tailer handle(Function<String, Boolean> lineFn) {this.lineFn = lineFn; this}
        /**
         * 设置处理线程池
         * @param exec
         */
        Tailer exec(Executor exec) {this.exec = exec; this}
        /**
         * 停止
         */
        void stop() {this.stopFlag = true}

        /**
         * tail 文件内容监控
         * @param file 文件全路径
         * @param follow 从最后第几行开始
         * @return
         */
        Tailer tail(String file, Integer follow = 5) {
            if (lineFn == null) lineFn = {println it}
            Runnable fn = {
                String tName = Thread.currentThread().name
                try {
                    Thread.currentThread().name = "Tailer-$file"
                    run(file, (follow == null ? 0 : follow))
                } catch (ex) {
                    log.error("Tail file " +file+ " error", ex)
                } finally {
                    Thread.currentThread().name = tName
                }
            }
            if (exec) {
                exec.execute { fn() }
            } else {
                th = new Thread({fn()}, "Tailer-$file")
                th.setDaemon(true)
                th.start()
            }
            this
        }

//        private void scan(String file, Integer follow) {
//            new Scanner(file)
//        }


        private void run(String file, Integer follow) {
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                Queue<String> buffer = follow ? new LinkedList<>() : null // 用来保存最后几行(follow)数据

                // 当前第一次到达文件结尾时,设为true
                boolean firstEnd
                String line
                while (!stopFlag) {
                    line = readLine(raf)
                    // line = raf.readLine()
                    if (line == null) { // 当读到文件结尾时(line为空即为文件结尾)
                        if (firstEnd) {
                            Thread.sleep(100L * new Random().nextInt(10))
                            // raf.seek(file.length()) // 重启定位到文件结尾(有可能文件被重新写入了)
                            continue
                        }
                        firstEnd = true
                        if (buffer) { // 第一次到达结尾后, 清理buffer数据
                            do {
                                line = buffer.poll()
                                if (line == null) break
                                stopFlag = !lineFn.apply(line)
                            } while (!stopFlag)
                            buffer = null
                        }
                    } else { // 读到行有数据
                        if (firstEnd) { // 直接处理行字符串
                            stopFlag = !lineFn.apply(line)
                        } else if (follow > 0) {
                            buffer.offer(line)
                            if (buffer.size() > follow) buffer.poll()
                        }
                    }
                }
            }
        }

        private String readLine(RandomAccessFile arf) {
            StringBuilder sb = new StringBuilder() // 默认的 readLine 用的 StringBuffer
            int c = -1
            boolean eol = false
            int n = '\n'
            int r = '\r'
            while (!eol) {
                switch (c = arf.read()) {
                    case -1:
                    case n:
                        eol = true
                        break
                    case r:
                        eol = true;
                        long cur = arf.getFilePointer();
                        if ((arf.read()) != n) {
                            arf.seek(cur);
                        }
                        break;
                    default:
                        sb.append((char)c);
                        break;
                }
            }

            if ((c == -1) && (sb.length() == 0)) {
                return null;
            }
            return sb.toString()
        }
    }


    /**
     * 把一个bean 转换成 一个map
     * @param bean
     * @return
     */
    static <T> ToMap toMapper(T bean) { return new ToMap(bean) }


    static class ToMap<T> {
        private T                     bean
        private Map<String, String>   propAlias
        private Set<String>           ignore
        private Map<String, Function> valueConverter
        private Map<String, Map<String, Function>> newProp
        private boolean               showClassProp
        private boolean               ignoreNull = false// 默认不忽略空值属性
        private Comparator<String>    comparator

        ToMap(T bean) { this.bean = bean }
        ToMap<T> aliasProp(String originPropName, String aliasName) {
            if (propAlias == null) propAlias = new HashMap<>(7)
            propAlias.put(originPropName, aliasName)
            return this
        }
        ToMap<T> showClassProp() { showClassProp = true; return this }
        ToMap<T> ignoreNull(ignoreNull = true) { this.ignoreNull = ignoreNull; return this }
        ToMap<T> sort(Comparator<String> comparator = Comparator.naturalOrder()) { this.comparator = comparator; return this }
        ToMap<T> ignore(String... propNames) {
            if (propNames == null) return this
            if (ignore == null) ignore = new HashSet<>(7)
            Collections.addAll(ignore, propNames)
            return this
        }
        ToMap<T> addConverter(String propName, Function converter) {
            if (valueConverter == null) valueConverter = new HashMap<>(7)
            valueConverter.put(propName, converter)
            return this
        }
        ToMap<T> addConverter(String originPropName, String newPropName, Function converter) {
            if (newProp == null) { newProp = new HashMap<>(); }
            newProp.computeIfAbsent(originPropName, s -> new HashMap<>(7)).put(newPropName, converter);
            return this;
        }
        Map build() {
            final Map map = comparator != null ? new TreeMap<>(comparator) : new LinkedHashMap();
            if (bean == null) return map
            def add = {String k, Object v ->
                if (ignore == null || !ignore.contains(k)) {
                    if (ignoreNull && v != null) map.put(k, v)
                    else if (!ignoreNull) map.put(k, v)
                }
            }
            iterateMethod(bean.getClass(), m -> {
                try {
                    if (void.class != m.returnType && m.getName().startsWith("get") && m.getParameterCount() == 0 && !MetaClass.isAssignableFrom(m.returnType)) { // 属性
                        String pName = m.getName().replace("get", "").uncapitalize()
                        String aliasName = null
                        if (propAlias != null && propAlias.containsKey(pName)) aliasName = propAlias.get(pName)
                        if ("class" == pName && !showClassProp) return
                        Object originValue = m.invoke(bean)

                        Object v = originValue
                        if (valueConverter != null) {
                            if (valueConverter.containsKey(pName)) {
                                v = valueConverter.get(pName).apply(originValue);
                            }
                            else if (aliasName != null && valueConverter.containsKey(aliasName)) {
                                v = valueConverter.get(aliasName).apply(originValue)
                            }
                        }
                        add((aliasName == null ? pName : aliasName), v)

                        if (newProp != null && newProp.containsKey(pName)) {
                            for (Iterator<Map.Entry<String, Function>> it = newProp.get(pName).entrySet().iterator(); it.hasNext(); ) {
                                Map.Entry<String, Function> e = it.next();
                                add(e.getKey(), e.getValue().apply(originValue));
                            }
                        }
                    }
                } catch (Exception ex) {
                    // ignore
                }
            })
            return map
        }
    }


    static <E> SafeList<E> safelist(Class<E> type) { new SafeList<E>()}


    static class SafeList<E> {
        protected final ArrayList<E> data = new ArrayList<>()
        final ReadWriteLock lock = new ReentrantReadWriteLock()

        E findAny(Function<E, Boolean> fn) {
            try {
                lock.readLock().lock()
                for (def e : data) {
                    if (fn.apply(e)) return e
                }
            } finally {
                lock.readLock().unlock()
            }
            null
        }

        /**
         * 随机取一个 元素
         * @param predicate 符合条件的元素
         * @return
         */
        E findRandom(Predicate<E> predicate = null) {
            try {
                lock.readLock().lock()
                if (data.isEmpty()) return null
                if (predicate == null) {
                    return data.get(new Random().nextInt(data.size()))
                } else {
                    def ls = data.findAll {predicate.test(it)}
                    if (ls.empty) return null
                    return ls.get(new Random().nextInt(ls.size()))
                }
            } finally {
                lock.readLock().unlock()
            }
            null
        }

        void withWriteLock(Runnable fn) {
            try {
                lock.writeLock().lock()
                fn?.run()
            } finally {
                lock.writeLock().unlock()
            }
        }

        void withReadLock(Runnable fn) {
            try {
                lock.readLock().lock()
                fn?.run()
            } finally {
                lock.readLock().unlock()
            }
        }

        Iterator<E> iterator() { data.iterator() }

        int size() { data.size() }

        boolean isEmpty() { data.isEmpty() }

        boolean contains(Object o) {
            try {
                lock.readLock().lock()
                return data.contains(o)
            } finally {
                lock.readLock().unlock()
            }
        }

        boolean remove(Object o) {
            try {
                lock.writeLock().lock()
                return data.remove(o)
            } finally {
                lock.writeLock().unlock()
            }
        }

        boolean add(E e) {
            try {
                lock.writeLock().lock()
                return data.add(e)
            } finally {
                lock.writeLock().unlock()
            }
        }
    }
}
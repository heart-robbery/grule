package core

import cn.xnatural.enet.event.EC
import cn.xnatural.enet.event.EL
import cn.xnatural.enet.event.EP
import core.module.ServerTpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.annotation.Resource
import java.lang.management.ManagementFactory
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

import static core.Utils.*
import static java.util.Collections.emptyList

class AppContext {
    protected final Logger log = LoggerFactory.getLogger(AppContext.class)
    @Lazy ConfigObject                  env          = initEnv()
    /**
     * 系统名字. 用于多个系统启动区别
     */
    @Lazy String                        name         = env['sys']['name'] ?: "gy"
    /**
     * 实例Id
     * NOTE: 保证唯一
     */
    @Lazy String                          id             = env['sys']["id"] ?: ((name ? name + "_" : '') + UUID.randomUUID().toString().replace('-', ''))
    /**
     * 系统运行线程池. {@link #initExecutor()}}
     */
    protected ThreadPoolExecutor          exec
    /**
     * 事件中心 {@link #initEp()}}
     */
    protected EP                          ep
    /**
     * 服务对象源
     */
    protected final Map<String, Object>   sourceMap      = new ConcurrentHashMap<>()
    /**
     * 对列执行器映射
     */
    @Lazy protected Map<String, Devourer> queue2Devourer = new ConcurrentHashMap<>()
    /**
     * 启动时间
     */
    final Date                            startup        = new Date()
    /**
     * jvm关闭钩子
     */
    @Lazy protected Thread                shutdownHook   = new Thread({
        // 通知各个模块服务关闭
        ep.fire("sys.stopping", EC.of(this).async(false).completeFn({ ec ->
            exec.shutdown()
            // 不删除的话会执行两遍
            // if (shutdownHook) Runtime.getRuntime().removeShutdownHook(shutdownHook)
        }))
    }, 'stop')


    /**
     * 初始化环境属性
     * @return
     */
    protected ConfigObject initEnv() {
        // 加载配置文件
        def cs = new ConfigSlurper()
        ConfigObject config = new ConfigObject()
        // 共用初始化属性
        config.put("baseDir", baseDir().canonicalPath.replaceAll('\\\\', '/'))
        cs.setBinding(config) // 共享配置属性(按顺序:后加载的属性,可以使用之前加载的属性)
        try {
            def ps = System.properties
            config.merge(cs.parse(ps)) // 方便 app.conf, app-[profile].conf 配置文件中使用系统属性

            // 首先加载 app.conf 配置文件
            def f = baseDir('conf/app.conf')
            if (f && f.exists()) {
                def s = f.getText('utf-8')
                if (s) config.merge(cs.parse(s))
            }

            // 加载 app-profile.conf 配置文件
            def profile = ps.containsKey('profile') ? ps.getProperty('profile') : config.getProperty('profile')
            if (profile) {
                f = baseDir("conf/app-${profile}.conf")
                if (f && f.exists()) {
                    def s = f.getText('utf-8')
                    if (s) config.merge(cs.parse(s))
                }
            }

            config.merge(cs.parse(ps)) // 系统属性优先级最高,所以最后覆盖一次
        } catch (ClassNotFoundException ex) {
            log.error("$ex.class.simpleName:$ex.message".toString())
        }
        config
    }


    /**
     * 启动
     */
    def start() {
        if (exec) throw new RuntimeException('App is running')
        log.info('Starting Application on {} with PID {}, active profile: ' + (env['profile']?:''), InetAddress.getLocalHost().getHostName(), pid())
        // 1. 初始化
        initExecutor()
        initEp()
        ep.addListenerSource(this)
        sourceMap.each{ k, v -> inject(v); ep.addListenerSource(v) }
        // 2. 通知所有服务启动
        ep.fire('sys.starting', EC.of(this).completeFn({ ec ->
            if (shutdownHook) Runtime.getRuntime().addShutdownHook(shutdownHook)
            sourceMap.each{ s, o -> inject(o)} // 自动注入
            log.info("Started Application "+ (name ? ('\'' + name + (id ? ':' + id : '') + '\'') : '') +" in {} seconds (JVM running for {})",
                (System.currentTimeMillis() - startup.getTime()) / 1000.0,
                ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0
            )
            ep.fire('sys.started', EC.of(this))
        }))
    }


    /**
     * 添加对象源
     * {@link #ep} 会找出source对象中所有其暴露的功能. 即: 用 @EL 标注的方法
     * 注: 为每个对象源都配一个 name 属性标识
     * @param source 不能是一个 Class
     */
    void addSource(
        Object source,
        String name = source instanceof ServerTpl ? source['name'] : (source?.class.simpleName.uncapitalize())
    ) {
        if (!source || !name) throw new IllegalArgumentException('source and name must be not empty')
        if ("sys".equalsIgnoreCase(name) || "env".equalsIgnoreCase(name) || "log".equalsIgnoreCase(name)) {
            log.error("Name property cannot equal 'sys', 'env' or 'log' . source: {}", source); return
        }
        if (sourceMap.containsKey(name)) {
            log.error("Name property '{}' already exist in source: {}", name, sourceMap.get(name)); return
        }
        sourceMap.put(name, source)
        if (ep) { inject(source); ep.addListenerSource(source) }
    }


    /**
     * 加入到对列行器执行函数
     * 每个对列里面的函数同一时间只执行一个, 各对列相互执行互不影响
     * @param qName 对列名
     * @param fn 要执行的函数
     * @return {@link Devourer}
     */
    Devourer queue(String qName = 'sys', Runnable fn) {
        def d = queue2Devourer.get(qName)
        if (d == null) {
            synchronized (this) {
                d = queue2Devourer.get(qName)
                if (d == null) {
                    d = new Devourer(qName, exec)
                    queue2Devourer.put(qName, d)
                }
            }
        }
        if (fn) d.offer(fn)
        return d
    }


    /**
     * 为bean对象中的{@link javax.annotation.Resource}注解字段注入对应的bean对象
     * @param o
     */
    @EL(name = "inject", async = false)
    void inject(Object o) {
        iterateField(o.getClass(), {f ->
            Resource aR = f.getAnnotation(Resource.class)
            if (aR) {
                try {
                    f.setAccessible(true)
                    Object v = f.get(o)
                    if (v) return // 已经存在值则不需要再注入

                    // 取值
                    if (EP.class.isAssignableFrom(f.getType())) v = wrapEpForSource(o)
                    else v = ep.fire("bean.get", EC.of(this).sync().args(f.type, aR.name())) // 全局获取bean对象

                    if (v == null) return
                    f.set(o, v)
                    log.trace("Inject @Resource field '{}' for object '{}'", f.name, o)
                } catch (ex) { log.error("Inject @Resource field '" + f.name + "' Error!", ex) }
            }
        })
    }


    /**
     * 全局查找 bean 对象
     * @param type 对象类型
     * @param name 对象名字
     * @return
     */
    def <T> T bean(Class<T> type, String name = null) { (T) ep.fire("bean.get", type, name) }


    /**
     * {@link #sourceMap}中查找对象
     * @param ec
     * @param bType bean 对象类型
     * @param bName bean 对象名字
     * @return bean 对象
     */
    @EL(name = ['bean.get', 'sys.bean.get'], async = false, order = -1f)
    protected localBean(EC ec, Class bType, String bName) {
        if (ec.result != null) return ec.result // 已经找到结果了, 就直接返回

        Object bean
        if (bName && bType) {
            bean = sourceMap.get(bName)
            if (!bean && !bType.isAssignableFrom(bean.getClass())) bean = null
        } else if (bName && !bType) {
            bean = sourceMap.get(bName)
        } else if (!bName && bType) {
            if (Executor.isAssignableFrom(bType) || ExecutorService.isAssignableFrom(bType)) bean = wrapExecForSource(ec.source())
            else if (AppContext.isAssignableFrom(bType)) bean = this
            else if (ConfigObject.isAssignableFrom(bType)) bean = env
            else if (EP.isAssignableFrom(bType)) bean = wrapEpForSource(ec.source())
            else {
                for (Map.Entry<String, Object> entry : sourceMap.entrySet()) {
                    if (bType.isAssignableFrom(entry.getValue().getClass())) {
                        bean = entry.getValue(); break
                    }
                }
            }
        }
        bean
    }


    /**
     * 初始化一个 {@link java.util.concurrent.ThreadPoolExecutor}
     * NOTE: 如果线程池在不停的创建线程, 有可能是因为 提交的 Runnable 的异常没有被处理.
     * see:  {@link java.util.concurrent.ThreadPoolExecutor#runWorker(java.util.concurrent.ThreadPoolExecutor.Worker)} 这里面当有异常抛出时 1128行代码 {@link java.util.concurrent.ThreadPoolExecutor#processWorkerExit(java.util.concurrent.ThreadPoolExecutor.Worker, boolean)}
     */
    protected initExecutor() {
        log.debug("init sys executor ... ")
        exec = new ThreadPoolExecutor(
            Integer.valueOf(env.sys.exec.corePoolSize?:8), Integer.valueOf(env.sys.exec.maximumPoolSize?:8),
            Long.valueOf(env.sys.exec.keepAliveTime?:120), TimeUnit.MINUTES,
            new LinkedBlockingQueue<>(),
            new ThreadFactory() {
                final AtomicInteger i = new AtomicInteger(1)
                @Override
                Thread newThread(Runnable r) {
                    return new Thread(r, "sys-" + i.getAndIncrement())
                }
            }
        ) {
            @Override
            void execute(Runnable fn) {
                try {
                    super.execute(fn)
                } catch (RejectedExecutionException ex) {
                    log.warn("Thread pool rejected new task very heavy load. {}", exec)
                } catch (Throwable t) {
                    log.error("Task Error", t)
                }
            }
        };
        exec.allowCoreThreadTimeOut(true)
    }


    /**
     * 初始化 EP
     * @return
     */
    protected initEp() {
        log.debug("init ep")
        ep = new EP(exec, LoggerFactory.getLogger(EP.class)) {
            @Override
            protected Object doPublish(String eName, EC ec) {
                if ("sys.starting" == eName || "sys.stopping" == eName || "sys.started" == eName) {
                    if (ec.source() != AppContext.this) throw new UnsupportedOperationException("not allow fire event '$eName'")
                }
                return super.doPublish(eName, ec)
            }
            @Override
            String toString() { "coreEp" }
        }
        // 添加 ep 跟踪事件
        def track = env.ep.track
        if (track instanceof String) {
            track = Arrays.stream(track.split(',')).filter{it?true:false}.map{it.trim()}.filter{it?true:false}.toArray()
        }
        ep.addTrackEvent(track as String[])
    }


    /**
     * 为 source 包装 Executor
     * @param source
     * @return {@link Executor}
     */
    protected Executor wrapExecForSource(Object source) {
        log.trace("wrapExecForSource: {}", source)
        return new ExecutorService() {
            @Override
            void shutdown() {}
            @Override
            List<Runnable> shutdownNow() { emptyList() }
            @Override
            boolean isShutdown() { exec.isShutdown() }
            @Override
            boolean isTerminated() { exec.isTerminated() }
            @Override
            boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
                exec.awaitTermination(timeout, unit)
            }
            @Override
            <T> Future<T> submit(Callable<T> task) { exec.submit(task) }
            @Override
            <T> Future<T> submit(Runnable task, T result) { exec.submit(task, result) }
            @Override
            Future<?> submit(Runnable task) { exec.submit(task) }
            @Override
            <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
                exec.invokeAll(tasks)
            }
            @Override
            <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
                exec.invokeAll(tasks, timeout, unit)
            }
            @Override
            <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
                exec.invokeAny(tasks)
            }
            @Override
            <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                exec.invokeAny(tasks, timeout, unit)
            }
            @Override
            void execute(Runnable cmd) { exec.execute(cmd) }
            int getCorePoolSize() { exec.corePoolSize }
            int getWaitingCount() { exec.queue.size() }
        }
    }


    /**
     * 为每个Source包装EP
     * @param source
     * @return {@link EP}
     */
    protected EP wrapEpForSource(Object source) {
        log.trace("wrapEpForSource: {}", source)
        return new EP() {
            @Override
            protected void init(Executor exec, Logger log) {}
            @Override
            EP addTrackEvent(String... eNames) { ep.addTrackEvent(eNames); return this }
            @Override
            EP delTrackEvent(String... eNames) { ep.delTrackEvent(eNames); return this }
            @Override
            EP removeEvent(String eName, Object s) {
                if (source != null && s != null && source != s) throw new UnsupportedOperationException("Only allow remove event of this source: $source")
                ep.removeEvent(eName, s); return this
            }
            @Override
            EP addListenerSource(Object s) { ep.addListenerSource(s); return this }
            @Override
            boolean exist(String... eNames) { return ep.exist(eNames) }
            @Override
            Object fire(String eName, EC ec) {
                if (ec.source() == null) ec.source(source)
                return ep.fire(eName, ec)
            }
            @Override
            String toString() {
                return "wrappedCoreEp: $source.class.simpleName"
            }
        }
    }
}
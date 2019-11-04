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

import static core.Utils.iterateField
import static core.Utils.pid
import static java.util.Collections.emptyList

class AppContext {
    static final    ConfigObject       env          = initEnv()
    protected final Logger             log          = LoggerFactory.getLogger(AppContext.class)
    /**
     * 系统名字. 用于多个系统启动区别
     */
    protected final String             name         = env.sys.name
    /**
     * 系统运行线程池. {@link #initExecutor()}}*/
    protected       ThreadPoolExecutor exec
    /**
     * 事件中心 {@link #initEp()}}*/
    protected       EP                 ep
    /**
     * 服务对象源
     */
    protected final Map<String, Object> sourceMap    = new ConcurrentHashMap<>()
    /**
     * 启动时间
     */
    protected final Date                startup      = new Date()
    /**
     * jvm关闭钩子
     */
    protected final Thread             shutdownHook = new Thread({
        // 通知各个模块服务关闭
        ep.fire("sys.stopping", EC.of(this).async(false).completeFn({ ec ->
            exec.shutdown()
            // 不删除的话会执行两遍
            // if (shutdownHook) Runtime.getRuntime().removeShutdownHook(shutdownHook)
        }))
    }, 'stop')


    // 初始化环境属性
    private static initEnv() {
        if (env != null) return env
        // 执行两次
        // println 'env =========== ' + env
        // 加载配置文件
        def cs = new ConfigSlurper()
        ConfigObject config = cs.parse(Class.forName('config'))
        def ps = System.properties
        def profile = ps.getProperty('profile')
        try {
            if (profile) {
                def c = cs.parse(Class.forName("config-$profile"))
                config.merge(c)
            }
        } catch (ClassNotFoundException ex) {
            println("$ex.class.simpleName:$ex.message")
        }
        config.merge(cs.parse(ps))
        config
    }


    /**
     * start
     * @return
     */
    def start() {
        if (exec) throw new RuntimeException('App is running')
        log.info('Starting Application on {} with PID {}, active profile: ' + env['profile'], InetAddress.getLocalHost().getHostName(), pid())
        // 1. 初始化系统线程池
        initExecutor()
        // 2. 初始化事件中心
        initEp(); ep.addListenerSource(this)
        sourceMap.each({k, v -> inject(v); ep.addListenerSource(v) })
        // 3. 通知所有服务启动
        ep.fire('sys.starting', EC.of(this).completeFn({ ec ->
            if (shutdownHook) Runtime.getRuntime().addShutdownHook(shutdownHook)
            sourceMap.each{s, o -> inject(o)} // 自动注入
            log.info("Started Application "+ (name ? '\'' + name + '\'' : '') +" in {} seconds (JVM running for {})",
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
     * @return
     */
    def addSource(
        Object source,
        String name = source instanceof ServerTpl ? source.name : (source?.class.simpleName.uncapitalize())
    ) {
        if (!source || !name) throw new IllegalArgumentException('source and name must be not empty')
        if ("sys".equalsIgnoreCase(name) || "env".equalsIgnoreCase(name) || "log".equalsIgnoreCase(name)) {
            log.warn("Name property cannot equal 'sys', 'env' or 'log' . source: {}", source); return
        }
        if (sourceMap.containsKey(name)) {
            log.warn("Name property '{}' already exist in source: {}", name, sourceMap.get(name)); return
        }
        sourceMap.put(name, source)
        if (ep) { inject(source); ep.addListenerSource(source) }
    }


    /**
     * 为bean对象中的{@link javax.annotation.Resource}注解字段注入对应的bean对象
     * @param o
     */
    @EL(name = "inject", async = false)
    protected def inject(Object o) {
        iterateField(o.getClass(), {f ->
            Resource r = f.getAnnotation(Resource.class);
            if (r == null) return
            try {
                f.setAccessible(true)
                Object v = f.get(o)
                if (v) return // 已经存在值则不需要再注入

                // 取值
                if (EP.class.isAssignableFrom(f.getType())) v = wrapEpForSource(o)
                else v = ep.fire("bean.get", EC.of(this).sync().args(f.getType(), r.name())) // 全局获取bean对象

                if (v == null) return
                f.set(o, v)
                log.trace("Inject @Resource field '{}' for object '{}'", f.getName(), o)
            } catch (Exception e) { log.error("Inject Error!", e) }
        })
    }


    /**
     * 全局查找Bean
     * @param type
     * @param <T>
     * @return
     */
    def <T> T bean(Class<T> type) { (T) ep.fire("bean.get", type) }


    /**
     * 查找对象
     * @param ec
     * @param beanType bean的类型
     * @param beanName bean 名字
     * @return bean 对象
     */
    @EL(name = ['bean.get', 'sys.bean.get'], async = false, order = 1f)
    protected def findLocalBean(EC ec, Class beanType, String beanName) {
        if (ec.result != null) return ec.result // 已经找到结果了, 就直接返回

        Object bean
        if (beanName && beanType) {
            bean = sourceMap.get(beanName)
            if (!bean && !beanType.isAssignableFrom(bean.getClass())) bean = null
        } else if (beanName && !beanType) {
            bean = sourceMap.get(beanName)
        } else if (!beanName && beanType) {
            if (Executor.isAssignableFrom(beanType) || ExecutorService.isAssignableFrom(beanType)) bean = wrapExecForSource(ec.source())
            else if (AppContext.isAssignableFrom(beanType)) bean = this
            else if (ConfigObject.isAssignableFrom(beanType)) bean = env
            else {
                for (Map.Entry<String, Object> entry : sourceMap.entrySet()) {
                    if (beanType.isAssignableFrom(entry.getValue().getClass())) {
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
    protected void initExecutor() {
        exec = new ThreadPoolExecutor(
            env.sys.exec.corePoolSize?:8, env.sys.exec.maximumPoolSize?:8, env.sys.exec.keepAliveTime?:30, TimeUnit.MINUTES, new LinkedBlockingQueue<>(),
            new ThreadFactory() {
                final AtomicInteger i = new AtomicInteger(1);
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
                    log.error("Task happen unknown error", t)
                }
            }
        };
        exec.allowCoreThreadTimeOut(true)
    }


    /**
     * 初始化 EP
     * @return
     */
    protected def initEp() {
        ep = new EP(exec, LoggerFactory.getLogger(EP.class)) {
            @Override
            protected Object doPublish(String eName, EC ec) {
                if ("sys.starting" == eName || "sys.stopping" == eName || "sys.started" == eName) {
                    if (ec.source() != AppContext.this) throw new UnsupportedOperationException("not allow fire event '$eName'")
                }
                if ("env.updateAttr" == eName) {
                    if (ec.source() != env) throw new UnsupportedOperationException("not allow fire event '$eName'")
                }
                return super.doPublish(eName, ec)
            }
            @Override
            String toString() { "coreEp" }
        }
        ep.addTrackEvent(env.ep.track as String[])
    }


    /**
     * 为 source 包装 Executor
     * @param source
     * @return {@link Executor}
     */
    protected Executor wrapExecForSource(Object source) {
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

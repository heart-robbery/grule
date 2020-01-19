package core.mode.task

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.text.SimpleDateFormat
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.LongAdder
import java.util.function.Consumer
import java.util.function.Predicate

/**
 * 任务{@link T}执行的上下文/管理者/执行容器, 提供任务{@link T}的管理及调度功能
 * 启动容器: {@link #start()}, 停止容器: {@link #stop()}
 * 管理多个任务的并行执行.包括任务的添加{@link #inWaitingQueue(T)}, 启动{@link #startTask(T)}与结束{@link #removeTask(T)}
 *
 * @param <T> Task 类型
 */
class TaskContext<T extends TaskWrapper> {
    protected final Logger   log
    /**
     * 所有任务通过 executor 执行
     */
    private         Executor executor
    /**
     * 是否为共享线程池
     */
    private         boolean  sharedExecutor
    /**
     * Context 唯一标识
     */
    protected       Object   key
    /**
     * 等待执行的任务对列
     */
    protected final Queue<T> waitingTasks       = new ConcurrentLinkedQueue<>()
    /**
     * 正在执行的任务对列
     */
    protected final Queue<T> executingTasks     = new ConcurrentLinkedQueue<>()
    /**
     * 容器能运行的Task最大个数限制, 即: 并行Task的个数限制
     * {@link #executingTasks}
     */
    private         int                         executingTaskLimit = 15
    /**
     * 一次性启动Task的个数限制.
     * 默认不限制 see: {@link #tryStartTask()}
     */
    protected       int                         oneTimeTaskLimit
    /**
     * 失败了多少个task
     */
    protected final LongAdder failureTasksCount = new LongAdder()
    /**
     * 成功了多少个task
     */
    protected final LongAdder                   successTasksCount  = new LongAdder()
    /**
     * 停止回调
     */
    private final   List<Consumer<TaskContext>> stopCallbacks = new LinkedList<>()
    /**
     * 开始时间
     */
    private         Date                        startTime
    /**
     * 结束时间
     */
    private         Date                        endTime
    /**
     * 容器状态, 是否运行状态
     */
    private final AtomicBoolean running = new AtomicBoolean(false)
    /**
     * 是否可以启动任务. 在某些情况下需要暂停启动任务
     */
    protected       boolean                     canStartTask       = true
    /**
     * 停止 信号(让此容器停止的信号)
     */
    protected       boolean                     stopSingle         = false
    /**
     * 容器状态, 是否为暂停状态
     */
    protected       boolean                     pause              = false
    /**
     * TODO 暂停 信号(让此容器暂停的信号)
     */
    private         boolean                     pauseSingle        = false
    @Lazy String logPrefix = "[${getKey()}] -> "


    TaskContext() {
        log = LoggerFactory.getLogger(getClass())
        // log = Log.of(getClass()).setPrefixSupplier(() -> "[" + getKey() + "] -> ")
    }


    /**
     * TaskContext 容器启动
     */
    final void start() {
        if (!running.compareAndSet(false, true)) {
            log.warn(logPrefix + "容器已经在运行了"); return
        }
        canStartTask = false
        log.info(logPrefix + "启动")
        this.startTime = (new Date())
        try {
            init()
            verifyBeforeStart()
            canStartTask = true
            if (waitingTasks.isEmpty()) stop()
            else tryStartTask()
        } catch (Throwable t) {
            log.error(logPrefix + "启动失败", t)
            stop()
        }
    }


    /**
     * TaskContext 容器关闭
     */
    private final void stop() {
        // running 已经为false了则忽略, 因为可能被同时多线程执行(同时执行checkShouldStop)
        if (!running.compareAndSet(true, false)) return
        log.trace(logPrefix + "停止")

        this.endTime = (new Date())
        // 依次关闭所有资源
        releaseResource()
        try {
            stopCallbacks.forEach(fn -> fn.accept(this))
        } catch (Exception e) {
            log.error(logPrefix + "stop回调错误", e)
        }
        try {
            doStop()
        } catch (Exception e) {
            log.error(logPrefix + "doStop错误", e)
        }
    }


    /**
     * 向此容器发送停止信号,
     * 执行后, TaskContext容器将不会接收新的Task, 继续把正在执行的Task执行完, 容器 就会自己关闭
     */
    synchronized void kill() {
        if (stopSingle) {
            log.warn(logPrefix + "已接收停止信号"); return
        }
        stopSingle = true
        log.info(logPrefix + "接收停止信号, 将会等待正在运行的Task执行完毕, 就自动关闭")
        checkShouldStop()
    }


    /**
     * 向当前容器中的 线程池 中 添加任务
     *
     * @param fn
     */
    final void exec(Runnable fn) {
        getExecutor().execute(fn)
    }


    /**
     * 启动一个 Task, Task开始走自己的生命周期
     * 如果 此方法为 private 就不必做验证了. 具体验证请看 {@link #tryStartTask}
     * @param task
     */
    private final void startTask(T task) {
        if (task == null) {
            log.warn(logPrefix + "启动Task为空, 忽略!")
            return
        }
        executingTasks.add(task)
        // 每个Task开始, 用一个新的执行栈
        exec(() -> task.start())
    }


    /**
     * 只要容器没有被关闭就可以启动Task.
     * 一般情况启动Task用这个方法: {@link #tryStartTask()}
     *
     * @param task
     */
    final void forceStartTask(T task) { if (running.get()) startTask(task) }


    /**
     * 尝试从排对对对列中 取出Task 并启动
     * 慢启动特性 {@link #oneTimeTaskLimit} 即: 不让容器正在行动的Task个数一下子到达最大值{@link #executingTaskLimit}
     *
     * @return 如果没有Task启动, 返回 false.
     */
    private final boolean tryStartTask() {
        boolean ret = false
        // 可以启动新Task的条件: 1.容器处于运行状态, 2.没有被通知stop, 3.没有处于暂停状态, 4.没有逻辑阻止启动新Task, 5.等待执行对列不为空, 6.执行对列没有达到最大个数
        final Predicate p = (ctx) -> (running.get() && !stopSingle && !pause && canStartTask() && !waitingTasks.isEmpty() && executingTasks.size() < executingTaskLimit)
        if (oneTimeTaskLimit > 0) {
            // TODO 如果有多个线程同时执行到这里, 慢启动特性就失效了
            int limit = oneTimeTaskLimit
            while (p.test(this) && limit > 0) {
                ret = true limit--
                startTask(waitingTasks.poll())
            }
        } else {
            while (p.test(this)) {
                ret = true
                startTask(waitingTasks.poll())
            }
        }
        // 当没有可启动的Task时, 检查是否有超时Task存在
//        if (!ret) {
//            // TODO: 有可能只有当等待对列执行完了, 才会执行到这里来
//            if (!executingTasks.isEmpty()) exec(() -> {
//                executingTasks.forEach(t -> {
//                    // NOTE: 多线程环境下, 有可能这个t 还没有执行start(), 也即是还没有开始Task
//                    if (t.getStartupTime() != null && t.isTimeout()) log.warn("检测到超时Task: {}", t.getKey())
//                })
//            })
//        }
        return ret
    }


    /**
     * 删除一个Task 之前 做的操作
     *
     * @param task
     */
    protected preRemoveTask(T task) {}


    /**
     * 删除一个Task, 一般是 在Task 结束后, 会调用此方法
     *
     * @param task
     */
    protected final void removeTask(final T task) {
        log.trace("移除Task: {}", task)
        preRemoveTask(task)
        if (task.isSuccessEnd()) successTasksCount.increment()
        else failureTasksCount.increment()
        executingTasks.remove(task) // 从执行对列中移除Task
        postRemoveTask(task)

        // 尝试启动新的Task, 如果没有可启动的Task, 则检查是否应该停止容器
        if (!tryStartTask()) checkShouldStop()
    }


    /**
     * 删除一个Task 之后 做的操作
     *
     * @param task
     */
    protected postRemoveTask(T task) {}


    /**
     * 进入等待执行对列
     *
     * @param task
     */
    final TaskContext<T> inWaitingQueue(final T task) {
        if (!running.get()) throw new RuntimeException("$key already stopped. Cannot add task: $task.key")
        if (task == null) {
            log.warn(logPrefix + "添加Task为空, 忽略!")
            return this
        }
        waitingTasks.offer(task)
        task.setCtx(this)
        postInWaiting(task)
        tryStartTask()
        return this
    }


    /**
     * 进入等待执行对列 后执行
     * @param task
     */
    protected postInWaiting(T task) {}


    /**
     * 子类实现以关闭一些资源等操作
     */
    protected doStop() {
        log.info(logPrefix + "结束. 成功了 {} 个Task, 失败了 {} 个Task, 共执行 " +(getEndTime().getTime() - getStartTime().getTime())+ " 毫秒",
            successTasksCount,
            failureTasksCount,
        )
    }


    /**
     * 子类 可在某种情况下 不让容器继续 启动Task
     *
     * @return
     */
    protected boolean canStartTask() { return this.canStartTask }


    /**
     * 启动前初始化
     */
    protected init() {}


    /**
     * 启动前验证
     */
    protected verifyBeforeStart() {
        if (waitingTasks.isEmpty()) log.warn(logPrefix + "没有可执行的Task")
    }


    /**
     * 检查是否应该结束当前 TaskContext 容器
     */
    private void checkShouldStop() {
        // 容器结束条件: 没有正在执行的任务, 不是暂停状态, 处于运行状态.
        if (executingTasks.isEmpty() && !pause && running.get()) {
            exec(() -> stop())
        }
    }


    /**
     * 释放资源
     */
    protected releaseResource() {
        try {
            if (!sharedExecutor && executor instanceof ExecutorService) {
                log.trace(logPrefix + "关闭线程池: {}", executor)
                ((ExecutorService) executor).shutdown()
            }
        } catch (Throwable e) {
            log.error(logPrefix + "线程池关闭错误", e)
        }
    }


    /**
     * 如果 没有设置共享线程池, 就创建一个私有的线程池
     *
     * @return
     */
    private Executor getExecutor() {
        if (executor == null) {
            executor = new ThreadPoolExecutor(2, 2, 10L, TimeUnit.MINUTES,
                new LinkedBlockingQueue<>(),
                new ThreadFactory() {
                    AtomicInteger cnt = new AtomicInteger(1)
                    @Override
                    Thread newThread(Runnable r) {
                        return new Thread(r, getKey() + "-" + cnt.getAndIncrement())
                    }
                })
            sharedExecutor = false
            log.info(logPrefix + "创建一个私有的线程池. executor: {}", executor)
        }
        return executor
    }


    final TaskContext setExecutor(Executor executor) {
        return setExecutor(executor, true)
    }


    /**
     * @param executor
     * @param shared   是否为共享线程池
     * @return
     */
    final TaskContext setExecutor(Executor executor, boolean shared) {
        if (running.get()) throw new UnsupportedOperationException("运行状态不能设置executor")
        Objects.requireNonNull(executor, "参数 executor 不能为空")
        this.executor = executor
        sharedExecutor = shared
        return this
    }


    /**
     * 注册 stop 回调
     *
     * @param callback
     */
    final void registerStopCallback(Consumer<TaskContext> callback) {
        Objects.requireNonNull(callback, "参数 callback is null")
        stopCallbacks.add(callback)
    }


    @Override
    boolean equals(Object obj) {
        if (this == obj) return true
        else if (obj instanceof TaskContext) {
            return Objects.equals(this.getKey(), ((TaskContext) obj).getKey())
        }
        return false
    }


    @Override
    String toString() {
        return Objects.toString(getKey(), "") +
            ": 成功了 " + successTasksCount + " 个Task, 失败了 " + failureTasksCount + " 个Task, 已执行 " +
            (System.currentTimeMillis() - getStartTime().getTime()) + " 毫秒, 正在排对 " + waitingTasks.size() + " 个, 正在执行的Task: " + executingTasks
    }


    /**
     * 结束时对当前 TaskContest 做一个 summary 信息摘要
     *
     * @return
     */
    String lastSummary() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        return Objects.toString(getKey(), "") +
            ": 开始于 " + sdf.format(getStartTime()) + ", 是否是手动停止: " + stopSingle + ", 成功了 " + successTasksCount + " 个Task, 失败了 " + failureTasksCount + " 个Task, 共执行 " +
            (getEndTime().getTime() - getStartTime().getTime()) + " 毫秒"
    }


    Object getKey() {
        if (key == null) {
            key = "TaskContext(" + Integer.toHexString(hashCode()) + ")"
        }
        return key
    }


    final TaskContext setKey(Object key) {
        if (running.get()) throw new UnsupportedOperationException("运行状态不能设置key")
        Objects.requireNonNull(key, "TaskContext的唯一标识不能为空")
        this.key = key
        return this
    }


    TaskContext attr(Object key, Object value) {
        attr(key, value)
        return this
    }


//    /**
//     * 得到一个 Spring Bean
//     *
//     * @param clz
//     * @param <K>
//     * @return
//     */
//    final <K> K getSpringBean(Class<K> clz) {
//        return getSpringCtx().getBean(clz)
//    }
//
//
//    final ApplicationContext getSpringCtx() {
//        return springCtx
//    }
//
//
//    TaskContext setSpringCtx(ApplicationContext springCtx) {
//        if (running.get()) throw new UnsupportedOperationException("运行状态不能设置")
//        Objects.requireNonNull(springCtx, "参数 springCtx 不能为空")
//        this.springCtx = springCtx
//        return this
//    }


    Date getStartTime() {
        if (startTime == null) startTime = new Date()
        return startTime
    }


    Date getEndTime() {
        return endTime
    }


    int getExecutingTaskLimit() {
        return executingTaskLimit
    }


    TaskContext setExecutingTaskLimit(Integer executingTaskLimit) {
        if (executingTaskLimit == null) {
            log.warn(logPrefix + "参数 executingTaskLimit is null, 使用默认值: " + this.executingTaskLimit)
            return this
        }
        this.executingTaskLimit = executingTaskLimit
        return this
    }
}

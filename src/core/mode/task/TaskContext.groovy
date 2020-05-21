package core.mode.task

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.text.SimpleDateFormat
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.LongAdder
import java.util.function.Predicate

/**
 * 任务{@link T}执行的上下文/管理者/执行容器, 提供任务{@link T}的管理及调度功能
 * 启动容器: {@link #start}, 停止容器: {@link #stop}
 * 启动前初始化 {@link #doStart}, 停止时执行 {@link #doStop}
 * 触发容器继续执行 {@link #trigger}
 * 管理多个任务的并行执行.包括任务的添加{@link #addTask}, 启动{@link #startTask}, 结束{@link #removeTask}
 *
 * @param <T> Task 类型
 */
class TaskContext<T extends TaskWrapper> {
    protected static final Logger log            = LoggerFactory.getLogger(TaskContext)
    /**
     * 所有任务通过 executor 执行
     */
    protected          Executor   executor
    /**
     * 是否为共享线程池
     */
    private         boolean       sharedExecutor
    /**
     * Context 唯一标识
     */
    @Lazy       String            key            = "TaskContext[" +Integer.toHexString(hashCode())+ "]"
    /**
     * 等待执行的任务对列
     */
    protected final Queue<T>      waitingTasks   = new ConcurrentLinkedQueue<>()
    /**
     * 正在执行的任务对列
     */
    protected final Queue<T>      executingTasks = new ConcurrentLinkedQueue<>()
    /**
     * 容器能运行的Task最大个数限制, 即: 并行Task的个数限制
     * {@link #executingTasks}
     */
    int                           parallelLimit  = 10
    /**
     * 一次性启动Task的个数限制.
     * 默认不限制 see: {@link #trigger()}
     */
    protected       int           oneTimeTaskLimit
    /**
     * 失败了多少个task
     */
    protected final LongAdder     failureCnt     = new LongAdder()
    /**
     * 成功了多少个task
     */
    protected final LongAdder                   successCnt    = new LongAdder()
    /**
     * 开始时间
     */
    final         Date              startTime     = new Date()
    /**
     * 结束时间
     */
    Date                     endTime
    /**
     * 容器状态, 是否运行状态
     */
    protected final AtomicBoolean                 running       = new AtomicBoolean(false)
    /**
     * 停止 信号(让此容器停止的信号)
     */
    protected       boolean       stopSingle     = false
    /**
     * 容器状态, 是否为暂停状态
     */
    protected       boolean       pause          = false
    @Lazy String                  logPrefix      = "[$key] -> "


    TaskContext(String key = null) { this.$key = key }


    /**
     * TaskContext 容器启动
     */
    final void start() {
        if (!running.compareAndSet(false, true)) {
            log.warn(logPrefix + "容器已经在运行了"); return
        }
        log.info(logPrefix + "启动")
        try {
            def f = doStart()
            if (waitingTasks.isEmpty()) {
                log.warn(logPrefix + "没有可执行的Task")
                tryStop()
            } else {
                if (f) trigger()
                else tryStop()
            }
        } catch (Exception t) {
            log.error(logPrefix + "启动失败", t)
            tryStop()
        }
    }


    /**
     * TaskContext 容器关闭
     */
    final void stop() {
        // running 已经为false了则忽略, 因为可能被同时多线程执行(同时执行checkShouldStop)
        if (!running.compareAndSet(true, false)) return
        log.trace(logPrefix + "停止")

        this.endTime = new Date()
        try {
            doStop()
        } catch (Exception e) {
            log.error(logPrefix + "stop回调错误", e)
        }

        log.info(logPrefix + "结束. 成功了 {} 个Task, 失败了 {} 个Task, 共执行 " +(getEndTime().getTime() - getStartTime().getTime())+ " 毫秒",
            successCnt,
            failureCnt,
        )

        // 依次关闭所有资源
        try {
            if (!sharedExecutor && executor instanceof ExecutorService) {
                log.trace(logPrefix + "关闭线程池: {}", executor)
                ((ExecutorService) executor).shutdown()
            }
        } catch (Exception e) {
            log.error(logPrefix + "线程池关闭错误", e)
        }
    }


    protected void doStop() {}


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
        tryStop()
    }


    /**
     * 暂停
     */
    TaskContext<T> suspend() {this.pause = true; this}


    /**
     * 恢复
     */
    TaskContext<T> resume() {this.pause = false; this}


    /**
     * 向当前容器中的 线程池 中 添加任务
     *
     * @param fn
     */
    protected final void exec(Runnable fn) {
        getExecutor().execute(fn)
    }


    /**
     * 启动一个 Task, Task开始走自己的生命周期
     * 如果 此方法为 private 就不必做验证了. 具体验证请看 {@link #trigger}
     * @param task
     */
    protected final void startTask(T task) {
        if (task == null) {
            log.warn(logPrefix + "启动Task为空, 忽略!")
            return
        }
        executingTasks.add(task)
        // 每个Task开始, 用一个新的执行栈
        exec(() -> task.start())
    }


    /**
     * 触发容器继续执行
     * 尝试从排对对对列中 取出Task 并启动
     * 慢启动特性 {@link #oneTimeTaskLimit} 即: 不让容器正在行动的Task个数一下子到达最大值{@link #parallelLimit}
     *
     * @return 如果没有Task启动, 返回 false.
     */
    protected final boolean trigger() {
        boolean ret = false
        // 可以启动新Task的条件: 1.容器处于运行状态, 2.没有被通知stop, 3.没有处于暂停状态, 4.没有逻辑阻止启动新Task, 5.等待执行对列不为空, 6.执行对列没有达到最大个数
        final Predicate p = (ctx) -> (running.get() && !stopSingle && !pause && !waitingTasks.isEmpty() && executingTasks.size() < parallelLimit)
        if (oneTimeTaskLimit > 0) {
            // TODO 如果有多个线程同时执行到这里, 慢启动特性就失效了
            int limit = oneTimeTaskLimit
            while (p.test(this) && limit > 0) {
                limit--
                def t = waitingTasks.poll()
                if (t) {
                    ret = true
                    startTask(t)
                }
            }
        } else {
            while (p.test(this)) {
                def t = waitingTasks.poll()
                if (t) {
                    ret = true
                    startTask(t)
                }
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
    protected void preRemoveTask(T task) {}


    /**
     * 删除一个Task, 一般是 在Task 结束后, 会调用此方法
     *
     * @param task
     */
    protected final void removeTask(final T task) {
        log.trace(logPrefix + "移除Task: {}", task)
        preRemoveTask(task)
        if (task.isSuccessEnd()) successCnt.increment()
        else failureCnt.increment()
        executingTasks.remove(task) // 从执行对列中移除Task
        postRemoveTask(task)

        // 尝试启动新的Task, 如果没有可启动的Task, 则检查是否应该停止容器
        if (!trigger()) tryStop()
    }


    /**
     * 删除一个Task 之后 做的操作
     *
     * @param task
     */
    protected void postRemoveTask(T task) {}


    /**
     * 添加任务
     * 进入等待执行对列
     *
     * @param task
     */
    final TaskContext<T> addTask(final T task) {
        if (endTime != null) throw new RuntimeException("$key already stopped. Cannot add task: $task.key")
        if (task == null) {
            log.warn(logPrefix + "添加Task为空, 忽略!")
            return this
        }
        waitingTasks.offer(task)
        task.setCtx(this)
        trigger()
        return this
    }


    /**
     * 启动前初始化
     * @return true 继续启动执行, false则关闭容器
     */
    protected boolean doStart() { true }


    /**
     * 检查是否应该结束当前 TaskContext 容器
     * @return true 可以停止, false 不能停止
     */
    protected boolean tryStop() {
        // 容器结束条件: 没有正在执行的任务, 不是暂停状态, 处于运行状态.
        if (executingTasks.isEmpty() && !pause && running.get()) {
            exec(() -> stop())
            return true
        }
        return false
    }


    /**
     * 如果 没有设置共享线程池, 就创建一个私有的线程池
     *
     * @return
     */
    Executor getExecutor() {
        if (executor == null) {
            executor = new ThreadPoolExecutor(2, 2, 1, TimeUnit.HOURS,
                new LinkedBlockingQueue<>(),
                new ThreadFactory() {
                    final AtomicInteger cnt = new AtomicInteger(1)
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


    /**
     * @param executor
     * @param shared   是否为共享线程池
     * @return
     */
    final TaskContext<T> setExecutor(Executor executor, boolean shared = true) {
        if (running.get()) throw new UnsupportedOperationException("运行状态不能设置executor")
        Objects.requireNonNull(executor, "参数 executor 不能为空")
        this.executor = executor
        sharedExecutor = shared
        return this
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
            ": 成功了 " + successCnt + " 个Task, 失败了 " + failureCnt + " 个Task, 已执行 " +
            (System.currentTimeMillis() - getStartTime().getTime()) + " 毫秒, 正在排对 " + waitingTasks.size() + " 个, 正在执行的Task: " + executingTasks
    }


    /**
     * 结束时对当前 TaskContest 做一个 summary 信息摘要
     * @return
     */
    String lastSummary() {
        if (endTime == null) return null
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        return Objects.toString(key, "") +
            ": 开始于 " + sdf.format(getStartTime()) + ", 是否是手动停止: " + stopSingle + ", 成功了 " + successCnt + " 个Task, 失败了 " + failureCnt + " 个Task, 共执行 " +
            (getEndTime().getTime() - getStartTime().getTime()) + " 毫秒"
    }
}

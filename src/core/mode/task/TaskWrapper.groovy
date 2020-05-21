package core.mode.task

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * {@link Step} 任务包装类
 * Task被折分成{@link #headStep}, {@link #processStep}, {@link #stopStep} 3个执行步骤依次执行
 * 重写 {@link #processStep} 可增添 执行步骤
 *
 * 启动: {@link #start}, 核心方法 {@link #trigger}
 * 任务方法执行顺序: {@link #pre}, {@link #process}, {@link #post}
 * Created by xxb on 18/1/10.
 */
class TaskWrapper {
    protected static final Logger                   log         = LoggerFactory.getLogger(TaskWrapper.class)
    // 所属TaskContext 执行容器/执行上下文
    protected          TaskContext           ctx
    /**
     * 是否已关闭
     */
    protected final AtomicBoolean stopped = new AtomicBoolean(false)
    /**
     * 是否应该关闭
     */
    private final   AtomicBoolean         shouldStop  = new AtomicBoolean(false)
    /**
     * 保存当前正在执行的Step
     */
    private AtomicReference<Step> currentStep = new AtomicReference<>()
    /**
     * 任务开始时间
     */
    private         Date                  startupTime
    /**
     * 任务结束时间
     */
    private         Date                  endTime
    /**
     * 任务唯一标识
     */
    @Lazy       Object                key = "Task(" + Integer.toHexString(hashCode()) + ")"
    /**
     * 日志前缀
     */
    @Lazy       String                logPrefix = "[${ctx().key}" + ":" +"${getKey()}] -> "
    /**
     * Task 超时时间, 单位毫秒
     * 默认10分钟.
     * TODO 检测超时的Task
     */
    protected       long                  timeout     = 1000 * 60 * 10


    /**
     * 快速定义一个 Task
     * @param fn task的执行函数
     * @return
     */
    static TaskWrapper of(Runnable fn) {
        return new TaskWrapper() {
            @Override
            protected process() { fn.run() }
        }
    }


    TaskWrapper(Object key) {
        this.key = key
        // 初始化 Log, 给每条日志添加前缀
        // log.setPrefixSupplier(() -> getLogPrefix())
    }

    TaskWrapper() {
        // 初始化 Log, 给每条日志添加前缀
        // log.setPrefixSupplier(() -> getLogPrefix())
    }


    /**
     * Task 启动
     */
    final void start() {
        if (stopped.get()) {
            log.warn(logPrefix + "已关闭"); return
        }
        log.debug(logPrefix + "启动")
        this.startupTime = (new Date())
        Step step = headStep()
        if (step == null) {
            log.error(logPrefix + "headStep is null, stop now ...")
            shouldStop()
            step = stopStep()
        }
        currentStep(step)
        trigger()
    }


    /**
     * 整个Task的执行被拆分成 各个 {@link Step} 执行
     * 主要包括3个Step: {@link #headStep()}, {@link #processStep()}, {@link #stopStep()}
     * 子类可重写上面3个Step, 以增加更多的Step. NOTE: 最后一个都必须以 {@link #stopStep()} 结束
     * 推荐: 执行步骤有可能会间断执行的用{@link Step}实现, 连续执行的步骤用方法实现
     * 此方法会遍历执行所有Step, 是整个Task的核心调度方法
     * 可手动调用此方法来触发Task继续执行下一个Step
     * NOTE: 要保证, 所有的Step 最终都必须要以 {@link core.mode.task.Step.StopStep} 结束
     * NOTE: 确保同一时间只有一个线程在执行此方法,即不能随便手动去调用此方法
     */
    final void trigger() {
        if (stopped.get()) return
        try {
            // step 为空有两种情况:
            // case 1:  Task执行条件未满足, 需要等到某个条件满足时, 重新调用此方法继续执行
            // case 2:  StopStep 即: 是最后个步骤被执行了
            Step step = currentStep()
            do {
                if (step == null) break
                currentStep(step)

                // 如果当前步骤 已执行完成, 就跳到下一个执行Step(case 1)
                if (step.isComplete()) step = step.next()
                else step = step.run()
            } while (step != null)
        } catch (Throwable t) {
            log.error(logPrefix + "错误: ", t)
            shouldStop()
            // 中途出错, 就强行关闭
            if (currentStep() != stopStep()) stopStep().run()
        }
    }


    /**
     * 第一个被执行的{@link Step}
     * @return
     */
    protected final Step headStep() {
        return new Step.ClosureStep(
            this,
            {
                log.trace(logPrefix + "head step ...")
                pre()
            },
            () -> processStep()
        )
    }


    protected pre() {}


    /**
     * 一般用于Task的 主逻辑执行的 {@link Step}
     * NOTE: 不必非得用到此步骤, 只要Task的{@link Step}链, 以 {@link #headStep()} 开始, 以 {@link #stopStep()} 结束都是可以的
     * @return
     */
    protected Step processStep() {
        return new Step.ClosureStep(
            this,
            {
                log.trace(logPrefix + "process step ...")
                process()
            },
            {stopStep()}
        )
    }


    protected process() {}


    /**
     * 关闭Task的 {@link core.mode.task.Step.StopStep}
     * @return {@link core.mode.task.Step.StopStep}
     */
    protected final Step.StopStep stopStep() {
        return new Step.StopStep(
            this,
            {
                try {
                    post()
                    if (isSuccessEnd()) log.info(logPrefix + "正常结束. 共执行: {} 毫秒" + (isTimeout() ? ", 超时" : ""), spendTime())
                    else log.warn(logPrefix + "非正常结束. 共执行: {} 毫秒" + (isTimeout() ? ", 超时" : ""), spendTime())
                } catch (Exception t) {
                    log.error(logPrefix + "关闭错误", t)
                } finally {
                    ctx().removeTask(this)
                }
            }
        )
    }


    /**
     * 任务结束方法
     */
    protected void post() {}


    /**
     * 设置应该关闭 Task
     * Task 在Task执行的过程中, 可调用此方法提前结束
     * @return 是否设置成功
     */
    final boolean shouldStop() {
        final boolean f = shouldStop.compareAndSet(false, true)
        if (f && currentStep() != null && currentStep().isWaitingNext()) {
            // 如果是暂停的状态需手动触发一下执行
            trigger()
        }
        return f
    }


    /**
     * 调用此方法后, Task 会执行完当前正在执行的 {@link Step}
     * 但执行完当前 {@link Step} 后不会进入下一个 {@link Step}, Task 会暂停运行
     * 例: 当前某个步骤 里面用了其它线程去做事, 并且 下一个 {@link Step} 的判断依赖其结果时, 必须先调用此方法
     */
    final TaskWrapper suspendNext() {
        currentStep().suspendNext()
        this
    }


    /**
     * 和 方法 {@link #suspendNext()} 配对执行
     * 即: 在 suspendNext() 中的新线程中回调此方法, 以保证 Task 继续执行
     */
    final TaskWrapper continueNext() {
        // 切回自己的执行器
        ctx.exec{currentStep().continueNext()}
        this
    }


    /**
     * @return 如果为true, 那么下一个步骤就应该是 {@link #stopStep()}
     */
    final boolean isShouldStop() {
        return shouldStop.get()
    }


    /**
     * @return 当前正在执行的步骤
     */
    final Step currentStep() {
        return currentStep.get()
    }


    /**
     * 改变当执行的步骤
     * @param step 之前的执行步骤
     * @return
     */
    final Step currentStep(Step step) {
        return currentStep.getAndSet(step)
    }



    /**
     * 是否成功结束
     * @return
     */
    boolean isSuccessEnd() {
        return !isShouldStop()
    }


    @Override
    String toString() {
        return getKey() + ""
    }


    /**
     * 计算Task共计花了多少时间(毫秒)
     * @return
     */
    long spendTime() {
        return (getEndTime().getTime() - getStartupTime().getTime())
    }



    TaskContext ctx() { return ctx }


    TaskWrapper setCtx(TaskContext ctx) {
        if (startupTime != null) {
            log.warn(logPrefix + "Task已启动了,ctx不能改变!")
            return this
        }
        this.ctx = ctx; return this
    }


    Date getStartupTime() {
        return startupTime
    }


    /**
     * Note: 只有在任务结束后才能调
     * @return
     */
    private final Date getEndTime() {
        if (endTime == null) endTime = new Date()
        return endTime
    }


    TaskWrapper setKey(Object key) {
        if (startupTime != null) {
            log.warn(logPrefix + "Task已启动了,key不能改变!")
            return this
        }
        this.key = key
        return this
    }


    boolean isTimeout() {
        // 如果 Timeout 小于0 则认为 永不超时
        return (getTimeout() > 0 && getStartupTime() != null && System.currentTimeMillis() - getStartupTime().getTime() > getTimeout())
    }


    TaskWrapper setTimeout(long timeout) {
        if (startupTime != null) {
            log.warn(logPrefix + "Task已启动了,timeout不能改变!")
            return this
        }
        this.timeout = timeout
        return this
    }


    long getTimeout() {
        return timeout
    }
}

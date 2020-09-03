package core

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

class Devourer {
    protected static final Logger          log         = LoggerFactory.getLogger(Devourer)
    protected final        Executor        exec
    protected final        AtomicBoolean   running     = new AtomicBoolean(false)
    // 任务执行对列
    protected final        Queue<Runnable> waiting     = new ConcurrentLinkedQueue<>()
    /**
     * 对列中失败的最大保留个数,
     * 如果 大于0 执行失败, 暂时保留, 直至 排对的个数大于此值
     * 否则 失败丢弃
     * 默认: 执行失败,丢弃
     */
    Integer                                failMaxKeep = 0
    final Object                           key


    Devourer(Object key, Executor exec) {
        if (key == null) throw new NullPointerException("devourer key is null")
        if (exec == null) throw new NullPointerException("executor is null")
        this.key = key
        this.exec = exec
    }


    /**
     * 任务入对列
     * @param fn 任务函数
     * @return {@link Devourer}
     */
    Devourer offer(Runnable fn) {
        if (fn == null) return this
        waiting.offer(fn)
        trigger()
        return this
    }


    /**
     * 不断的从 {@link #waiting} 对列中取出执行
     */
    protected void trigger() {
        if (waiting.isEmpty()) return
        // TODO 会有 cas aba 问题?
        if (!running.compareAndSet(false, true)) return
        // 1.必须保证这里只有一个线程被执行
        // 2.必须保证这里waiting对列中不为空
        // 3.必须保证不能出现情况: waiting 对列中有值, 但没有被执行
        exec.execute {
            Runnable task
            try {
                task = waiting.peek()
                if (task != null) {
                    task.run()
                    waiting.poll() // 避免 peek 出来null, 但poll 出来不为null(有可能在peek和poll 之前插入了新的Task)
                }
            } catch (Throwable t) {
                if (task && failMaxKeep && (waitingCount > failMaxKeep)) waiting.poll()
                log.error(Devourer.simpleName + ": " + key, t)
            } finally {
                running.set(false)
                if (!waiting.isEmpty()) trigger() // 持续不断执行对列中的任务
            }
        }
    }


    /**
     * 排对个数
     * @return
     */
    int getWaitingCount() { waiting.size() }


    /**
     * 执行失败时, 保留最大个数
     * NOTE: 失败的任务会不断的重试执行, 直到成功或者对列中的个数大于此值被删除
     * 典型应用: 数据上报场景
     * @return
     */
    Devourer failMaxKeep(Integer maxKeep) {this.failMaxKeep = maxKeep; this}


    /**
     * 是否正在运行
     * @return
     */
    boolean isRun() {running.get()}


    void shutdown() {
        if (exec instanceof ExecutorService) ((ExecutorService) exec).shutdown()
    }


    @Override
    String toString() {
        return "running: " + running + ", waiting count: " + waiting.size()
    }
}

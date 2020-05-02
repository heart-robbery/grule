package core

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

class Devourer {
    protected static final Logger              log     = LoggerFactory.getLogger(Devourer)
    protected final        Executor            exec
    protected final        AtomicBoolean       running = new AtomicBoolean(false)
    protected final        Queue<Runnable>     waiting = new ConcurrentLinkedQueue<>()
    final Object key


    Devourer(Object key, Executor exec) {
        if (key == null) throw new NullPointerException("devourer key is null")
        if (exec == null) throw new NullPointerException("executor is null")
        this.key = key
        this.exec = exec
    }


    Devourer offer(Runnable fn) {
        if (fn == null) return this
        waiting.offer(fn)
        trigger()
        return this
    }


    /**
     * 不断的从 {@link #waiting} 对列中取出执行
     */
    protected trigger() {
        if (waiting.isEmpty()) return
        // TODO 会有 cas aba 问题?
        if (!running.compareAndSet(false, true)) return
        // 1.必须保证这里只有一个线程被执行
        // 2.必须保证这里waiting对列中不为空
        // 3.必须保证不能出现情况: waiting 对列中有值, 但没有被执行
        exec.execute{
            try {
                Runnable t = waiting.poll()
                if (t != null) t.run()
            } catch (Throwable t) {
                log.error("$Devourer.class.simpleName: " + key, t)
            } finally {
                running.set(false)
                if (!waiting.isEmpty()) trigger()
            }
        }
    }


    /**
     * 排对个数
     * @return
     */
    int getWaitingCount() { waiting.size() }



    void shutdown() {
        if (exec instanceof ExecutorService) ((ExecutorService) exec).shutdown()
    }


    @Override
    String toString() {
        return "running: " + running + ", waiting count: " + waiting.size()
    }
}

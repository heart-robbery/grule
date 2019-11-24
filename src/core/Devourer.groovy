package core

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.function.Supplier

class Devourer {
    protected final Logger              log     = LoggerFactory.getLogger(getClass())
    protected final Executor            exec
    protected final AtomicBoolean       running = new AtomicBoolean(false)
    protected final Queue<Runnable>     waiting = new ConcurrentLinkedQueue<>()
    protected final Object              key
    /**
     * 是否应该熔断: 暂停执行
     */
    protected       Supplier<Boolean>   pause   = { Boolean.FALSE }
    /**
     * 是否应该熔断: 丢弃
     */
    protected       Supplier<Boolean>   fusing  = { Boolean.FALSE }
    /**
     * 异常处理
     */
    protected       Consumer<Throwable> exConsumer


    Devourer(Object key, Executor exec) {
        if (key == null) throw new NullPointerException("devourer key is null")
        if (exec == null) throw new NullPointerException("executor is null")
        this.key = key
        this.exec = exec
    }


    Devourer offer(Runnable fn) {
        if (fn == null || fusing.get()) return this
        waiting.offer(fn)
        trigger()
        return this
    }


    /**
     * 不断的从 {@link #waiting} 对列中取出执行
     */
    protected def trigger() {
        if (waiting.isEmpty() || pause.get()) return
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
                if (exConsumer == null) log.error(getClass().simpleName + ":" + key, t)
                else exConsumer.accept(t)
            } finally {
                running.set(false)
                if (!waiting.isEmpty()) trigger()
            }
        }
    }


    Devourer pause(Supplier<Boolean> pause) {
        if (pause == null) throw new IllegalArgumentException("pause Supplier can not be null")
        this.pause = pause
        return this
    }


    Devourer fusing(Supplier<Boolean> fusing) {
        if (fusing == null) throw new IllegalArgumentException("fusing Supplier can not be null")
        this.fusing = fusing
        return this
    }


    Devourer exConsumer(Consumer<Throwable> exConsumer) {
        this.exConsumer = exConsumer
        return this
    }


    /**
     * 排对个数
     * @return
     */
    int getWaitingCount() { waiting.size() }



    def shutdown() {
        if (exec instanceof ExecutorService) ((ExecutorService) exec).shutdown()
    }


    @Override
    String toString() {
        return "running: " + running + ", waiting count: " + waiting.size()
    }
}

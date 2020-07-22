package core.module

import cn.xnatural.enet.event.EL
import cn.xnatural.enet.event.EP
import core.Utils
import org.quartz.*
import org.quartz.impl.StdSchedulerFactory
import org.quartz.impl.triggers.SimpleTriggerImpl
import org.quartz.spi.OperableTrigger
import org.quartz.spi.ThreadPool

import java.text.SimpleDateFormat
import java.time.Duration
import java.util.concurrent.Executor
import java.util.function.Supplier

/**
 * 定时任务工具服务类
 */
class SchedSrv extends ServerTpl {
    static final F_NAME  = 'sched'
    private static final KEY_FN = "fn"
    protected Scheduler scheduler
    
    
    SchedSrv() { super(F_NAME) }


    @EL(name = "sys.starting", async = true)
    void start() {
        if (scheduler) throw new RuntimeException("$name is already running")
        if (ep == null) {ep = new EP(exec); ep.addListenerSource(this)}

        StdSchedulerFactory f = new StdSchedulerFactory()
        Properties p = new Properties(); p.putAll(attrs())
        p.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_CLASS, AgentThreadPool.class.getName())
        f.initialize(p)
        AgentThreadPool.exec = exec
        scheduler = f.getScheduler()
        scheduler.start()
        exposeBean(scheduler)
        log.info("Started {}(Quartz) Server", name)
        ep.fire("${name}.started")
    }


    @EL(name = "sys.stopping", async = true)
    void stop() {
        log.debug("Shutdown '{}'(Quartz) Server", name)
        scheduler?.shutdown(); scheduler = null
        AgentThreadPool.exec = null
    }


    /**
     * cron 时间表达式
     * @param cron
     * @param fn
     */
    @EL(name = "sched.cron", async = false)
    Trigger cron(String cron, Runnable fn) {
        if (!scheduler) throw new RuntimeException("$name is not running")
        if (!cron || !fn) throw new IllegalArgumentException("'cron' and 'fn' must not be empty")
        final JobDataMap data = new JobDataMap()
        data.put(KEY_FN, fn)
        String id = cron + "_" + System.currentTimeMillis()
        Trigger trigger = TriggerBuilder.newTrigger()
            .withIdentity(new TriggerKey(id, "cron"))
            .withSchedule(CronScheduleBuilder.cronSchedule(cron))
            .build()
        Date d = scheduler.scheduleJob(
            JobBuilder.newJob(JopTpl.class).withIdentity(id, "cron").setJobData(data).build(),
            trigger
        )
        log.info("add cron '{}' job will execute last time '{}'", id, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss SSS").format(d))
        trigger
    }


    /**
     * 在多少时间之后执行
     * @param time
     * @param fn
     */
    @EL(name = "sched.after", async = false)
    Trigger after(Duration time, Runnable fn) {
        if (!scheduler) throw new RuntimeException("$name is not running")
        if (!time || !fn) throw new IllegalArgumentException("'time', and 'fn' must not be null")
        final JobDataMap data = new JobDataMap()
        data.put(KEY_FN, fn)
        String id = time.toMillis() + "_" + Utils.random(8)
        SimpleDateFormat sdf = new SimpleDateFormat("ss mm HH dd MM ? yyyy")
        String cron = sdf.format(new Date(new Date().getTime() + time.toMillis()))
        Trigger trigger = TriggerBuilder.newTrigger()
            .withIdentity(new TriggerKey(id, "after"))
            .withSchedule(CronScheduleBuilder.cronSchedule(cron))
            .build()
        Date d = scheduler.scheduleJob(
            JobBuilder.newJob(JopTpl.class).withIdentity(id, "after").setJobData(data).build(),
            trigger
        )
        log.debug("add after '{}' job will execute at '{}'", id, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss SSS").format(d))
        trigger
    }


    /**
     * 在将来的某个时间点执行
     * @param time
     * @param fn
     */
    @EL(name = "sched.time", async = false)
    Trigger time(Date time, Runnable fn) {
        if (scheduler) throw new RuntimeException(getName() + " is not running")
        if (!time || !fn) throw new IllegalArgumentException("'time' and 'fn' must not be null")
        final JobDataMap data = new JobDataMap()
        data.put(KEY_FN, fn)
        String id = time + "_" + Utils.random(8)
        SimpleDateFormat sdf = new SimpleDateFormat("ss mm HH dd MM ? yyyy")
        String cron = sdf.format(time)
        Trigger trigger = TriggerBuilder.newTrigger()
            .withIdentity(new TriggerKey(id, "time"))
            .withSchedule(CronScheduleBuilder.cronSchedule(cron))
            .build()
        Date d = scheduler.scheduleJob(
            JobBuilder.newJob(JopTpl.class).withIdentity(id, "time").setJobData(data).build(),
            trigger
        )
        log.info("add time '{}' job will execute at '{}'", id, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss SSS").format(d))
        trigger
    }


    /**
     * 动态任务调度执行. 自定义下次执行时间
     * @param fn 任务函数
     * @param nextDateGetter
     *      下次触发时间计算函数. 函数返回下次触发时间. 如果返回空 则停止
     *      NOTE: 执行函数之前会计算下次执行的时间
     * @return {@link OperableTrigger}
     */
    @EL(name = "sched.dyn", async = false)
    Trigger dyn(Runnable fn, Supplier<Date> nextDateGetter) {
        assert scheduler : "$name is not running"
        assert nextDateGetter : "$nextDateGetter must not be null"
        final JobDataMap data = new JobDataMap()
        data.put(KEY_FN, fn)
        String id = "dyn_" + Utils.random(8)
        def startFireTime = nextDateGetter.get()
        assert startFireTime : "函数未算出触发时间"

        final Queue<Tuple2<Integer, Date>> count = new LinkedList<>()
        OperableTrigger trigger = new SimpleTriggerImpl() {
            // org.quartz.simpl.RAMJobStore.triggersFired 1567/1568行 此方法连续执行了两遍
            // 此方法是在每次执行任务之前调用, 用来计算下次执行时间
            @Override
            void triggered(Calendar calendar) {
                setTimesTriggered(getTimesTriggered() + 1)
                setPreviousFireTime(getNextFireTime())
                def t = count.find {it.v1 == getTimesTriggered()}
                if (t) setNextFireTime(t.v2) // 保证两次连续执行时, 计算出来的下次执行时间相同
                else {
                    def d = nextDateGetter.get()
                    setNextFireTime(d)
                    if (count.size() > 3) count.poll()
                    count.offer(Tuple.tuple(getTimesTriggered(), d))
                }
            }
        }
        trigger.setKey(new TriggerKey(id, "dyn"))
        trigger.setStartTime(startFireTime)

        Date d = scheduler.scheduleJob(JobBuilder.newJob(JopTpl.class).withIdentity(id, "dyn").setJobData(data).build(), trigger)
        log.info("add dyn '{}' job will execute at '{}'", id, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss SSS").format(d))
        trigger
    }
    

    static class JopTpl implements Job {
        @Override
        void execute(JobExecutionContext ctx) throws JobExecutionException {
            ((Runnable) ctx.getMergedJobDataMap().get(KEY_FN))?.run()
        }
    }


    /**
     * 代理线程池
     */
    static class AgentThreadPool implements ThreadPool {
        static Executor exec
        @Override
        boolean runInThread(Runnable fn) {
            if (exec == null) fn.run()
            else exec.execute(fn)
            return true
        }

        @Override
        int blockForAvailableThreads() { return 1 } // 为1 就是每次 取一个距离时间最近的一个trigger org.quartz.simpl.RAMJobStore.acquireNextTriggers timeTriggers.first()

        @Override
        void initialize() throws SchedulerConfigException { }

        @Override
        void shutdown(boolean waitForJobsToComplete) { }

        @Override
        int getPoolSize() { return -1 }

        @Override
        void setInstanceId(String schedInstId) { }

        @Override
        void setInstanceName(String schedName) { }
    }
}

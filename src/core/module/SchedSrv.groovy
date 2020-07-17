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
            JobBuilder.newJob(JopTpl.class).withIdentity(id).setJobData(data).build(),
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
        if (!time || !fn) throw new IllegalArgumentException("'time', 'unit' and 'fn' must not be null")
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
            JobBuilder.newJob(JopTpl.class).withIdentity(id).setJobData(data).build(),
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
            JobBuilder.newJob(JopTpl.class).withIdentity(id).setJobData(data).build(),
            trigger
        )
        log.info("add time '{}' job will execute at '{}'", id, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss SSS").format(d))
        trigger
    }


    /**
     *
     * 动态任务调度执行
     * @param fn 任务函数
     * @param fireDateFn 触发时间函数. 函数返回下次触发时间. 如果返回空 则停止
     * @return {@link OperableTrigger}
     */
    @EL(name = "sched.dyn", async = false)
    OperableTrigger dyn(Runnable fn, Supplier<Date> fireDateFn) {
        assert scheduler : "$name is not running"
        assert fireDateFn : "$fireDateFn must not be null"
        final JobDataMap data = new JobDataMap()
        data.put(KEY_FN, fn)
        String id = "dyn_" + Utils.random(8)
        def startFireTime = fireDateFn.get()
        assert startFireTime : "函数未算出触发时间"

        OperableTrigger trigger = new SimpleTriggerImpl() {
            @Override
            Date getNextFireTime() {
                def d = fireDateFn.get()
                d = d == null ? new Date() : d
                setNextFireTime(d)
                return d
            }
        }
        trigger.setKey(new TriggerKey(id, "dyn"))
        trigger.setStartTime(startFireTime)

        Date d = scheduler.scheduleJob(
            JobBuilder.newJob(JopTpl.class).withIdentity(id).setJobData(data).build(),
            trigger
        )
        log.info("add dyn '{}' job will execute at '{}'", id, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss SSS").format(d))
        trigger
    }
    

    static class JopTpl implements Job {
        @Override
        void execute(JobExecutionContext ctx) throws JobExecutionException {
            ((Runnable) ctx.getMergedJobDataMap().get(KEY_FN)).run()
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
        int blockForAvailableThreads() { return 1 }

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

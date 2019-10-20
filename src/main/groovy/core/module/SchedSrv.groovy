package core.module

import cn.xnatural.enet.event.EL
import cn.xnatural.enet.event.EP
import org.quartz.*
import org.quartz.impl.StdSchedulerFactory
import org.quartz.spi.ThreadPool

import javax.annotation.Resource
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService

class SchedSrv extends ServerTpl {
    static final F_NAME  = 'sched'
    @Resource
    protected Executor exec
    protected Scheduler scheduler
    
    
    SchedSrv() { super(F_NAME) }


    @EL(name = "sys.starting")
    def start() {
        if (scheduler) throw new RuntimeException("$name is already running")
        if (ep == null) {ep = new EP(exec) ep.addListenerSource(this)}

        StdSchedulerFactory f = new StdSchedulerFactory()
        Properties p = new Properties(); p.putAll(attrs)
        p.setProperty(StdSchedulerFactory.PROP_THREAD_POOL_CLASS, AgentThreadPool.class.getName())
        f.initialize(p)
        AgentThreadPool.exec = exec
        scheduler = f.getScheduler()
        scheduler.start()
        exposeBean(scheduler)
        log.info("Started {}(Quartz) Server", name)
        ep.fire("${name}.started")
    }


    @EL(name = "sys.stopping")
    def stop() {
        log.debug("Shutdown '{}'(Quartz) Server", name)
        scheduler?.shutdown(); scheduler = null
        AgentThreadPool.exec = null
        if (exec instanceof ExecutorService) ((ExecutorService) exec).shutdown()
    }


    /**
     * cron 时间表达式
     * @param cron
     * @param fn
     */
    @EL(name = "sched.cron", async = false)
    def cron(String cron, Runnable fn) {
        if (!scheduler) throw new RuntimeException("$name is not running")
        if (!cron || !fn) throw new IllegalArgumentException("'cron' and 'fn' must not be empty")
        JobDataMap data = new JobDataMap()
        data.put("fn", fn)
        String id = cron + "_" + System.currentTimeMillis()
        Date d = scheduler.scheduleJob(
            JobBuilder.newJob(JopTpl.class).withIdentity(id).setJobData(data).build(),
            TriggerBuilder.newTrigger()
                .withIdentity(new TriggerKey(id, "default"))
                .withSchedule(CronScheduleBuilder.cronSchedule(cron))
                .build()
        )
        log.info("add cron '{}' job will execute last time '{}'", id, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss SSS").format(d))
    }


    /**
     * 在多少时间之后执行
     * @param time
     * @param fn
     */
    @EL(name = "sched.after")
    def after(Duration time, Runnable fn) {
        if (!scheduler) throw new RuntimeException("$name is not running")
        if (!time || !fn) throw new IllegalArgumentException("'time', 'unit' and 'fn' must not be null")
        JobDataMap data = new JobDataMap()
        data.put("fn", fn)
        String id = time.toMillis() + "_" + UUID.randomUUID().toString()
        SimpleDateFormat sdf = new SimpleDateFormat("ss mm HH dd MM ? yyyy")
        String cron = sdf.format(new Date(new Date().getTime() + time.toMillis()))
        Date d = scheduler.scheduleJob(
            JobBuilder.newJob(JopTpl.class).withIdentity(id).setJobData(data).build(),
            TriggerBuilder.newTrigger()
                .withIdentity(new TriggerKey(id, "default"))
                .withSchedule(CronScheduleBuilder.cronSchedule(cron))
                .build()
        )
        log.debug("add after '{}' job will execute at '{}'", id, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss SSS").format(d))
    }


    /**
     * 在将来的某个时间点执行
     * @param time
     * @param fn
     */
    @EL(name = "sched.time")
    def time(Date time, Runnable fn) {
        if (scheduler) throw new RuntimeException(getName() + " is not running")
        if (!time || !fn) throw new IllegalArgumentException("'time' and 'fn' must not be null")
        JobDataMap data = new JobDataMap()
        data.put("fn", fn)
        String id = time + "_" + UUID.randomUUID().toString()
        SimpleDateFormat sdf = new SimpleDateFormat("ss mm HH dd MM ? yyyy")
        String cron = sdf.format(time)
        Date d = scheduler.scheduleJob(
            JobBuilder.newJob(JopTpl.class).withIdentity(id).setJobData(data).build(),
            TriggerBuilder.newTrigger()
                .withIdentity(new TriggerKey(id, "default"))
                .withSchedule(CronScheduleBuilder.cronSchedule(cron))
                .build()
        )
        log.info("add time '{}' job will execute at '{}'", id, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss SSS").format(d))
    }
    

    static class JopTpl implements Job {
        @Override
        void execute(JobExecutionContext ctx) throws JobExecutionException {
            ((Runnable) ctx.getMergedJobDataMap().get("fn")).run()
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

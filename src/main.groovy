import cn.xnatural.app.AppContext
import cn.xnatural.app.ServerTpl
import cn.xnatural.enet.event.EL
import cn.xnatural.enet.event.EP
import cn.xnatural.jpa.Repo
import cn.xnatural.remoter.Remoter
import cn.xnatural.sched.Sched
import core.CacheSrv
import core.HttpSrv
import core.OkHttpSrv
import core.RedisClient
import ctrl.*
import entity.*
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import service.FileUploader
import service.TestService
import service.rule.*

import java.time.Duration
import java.util.function.Supplier

System.setProperty("configdir", "../conf")
@Field final Logger log = LoggerFactory.getLogger(getClass())
@Field final AppContext app = new AppContext() //应用上下文
@Lazy @Field EP ep = app.bean(EP, null)


// 系统功能添加区
app.addSource(new OkHttpSrv()) // http 客户端
app.addSource(new CacheSrv()) // 对象缓存
app.addSource(new ServerTpl("sched") { // 定时任务
    Sched sched
    @EL(name = "sys.starting", async = true)
    void start() {
        sched = new Sched(attrs(), exec()).init()
        exposeBean(sched)
        ep.fire("${name}.started")
    }
    @EL(name = "sched.after")
    void after(Duration duration, Runnable fn) {sched.after(duration, fn)}
    @EL(name = "sched.time")
    void time(Date time, Runnable fn) {sched.time(time, fn)}
    @EL(name = "sched.cron")
    void cron(String cron, Runnable fn) {sched.cron(cron, fn)}
    @EL(name = "sched.dyn")
    void dyn(Supplier<Date> dateSupplier, Runnable fn) {sched.dyn(dateSupplier, fn)}
    @EL(name = "sys.stopping", async = true)
    void stop() { sched?.stop() }
})
app.addSource(new ServerTpl("remoter") {
    Remoter remoter
    @EL(name = "sched.started")
    void start() {
        remoter = new Remoter(app.name(), app.id(), attrs(), exec(), ep, bean(Sched))
        exposeBean(remoter)
        exposeBean(remoter.aioClient)
        ep.fire("${name}.started")
    }

    @EL(name = 'sys.heartbeat', async = true)
    void heartbeat() {
        remoter.sync()
        remoter.aioServer.clean()
    }
    @EL(name = "sys.stopping", async = true)
    void stop() { remoter.stop() }
}) // 集群分布式
app.addSource(new ServerTpl("jpa_rule") { //数据库 jpa_rule
    Repo repo
    @EL(name = "sys.starting", async = true)
    void start() {
        repo = new Repo(attrs()).entities( // jpa封装
            Decision, RuleField, DataCollector, OpHistory, DecideRecord, CollectRecord,
            User, Permission, GlobalConfig
        ).init()
        exposeBean(repo, name + "_repo")
        ep.fire("${name}.started")
    }

    @EL(name = "sys.stopping", async = true)
    void stop() { repo?.close() }
})
app.addSource(new HttpSrv().ctrls(
        TestCtrl, MainCtrl, RuleCtrl, MntCtrl, MntUserCtrl, MntDecisionCtrl, MntAnalyseCtrl
))
app.addSource(new FileUploader())
app.addSource(new TestService())
app.addSource(new FieldManager())
app.addSource(new DecisionSrv())
app.addSource(new DecisionManager())
app.addSource(new OpHistorySrv())
app.addSource(new UserSrv())
app.addSource(this)
app.start() // 启动系统


@EL(name = 'sys.inited')
void sysInited() {
    if (app.attrs('redis')) { //根据配置是否有redis,创建redis客户端工具
        app.addSource(new RedisClient())
    }
}


@EL(name = 'sys.started', async = true)
void sysStarted() {
    try {
    } finally {
        // System.exit(0)
    }
}


/**
 * 系统心跳 清理
 */
@EL(name = 'sys.heartbeat', async = true)
void heartbeat() {
    // 删除 Classloader 中只进不出的 parallelLockMap
    def field = ClassLoader.getDeclaredField('parallelLockMap')
    field.setAccessible(true)
    Map<String, Object> m = field.get(Thread.currentThread().contextClassLoader.parent.parent)
    m.clear()
    return
    if (m != null) {
        for (def itt = m.iterator(); itt.hasNext(); ) {
            def entry = itt.next()
            if ((entry.key.startsWith("script") && entry.key.endsWith(".groovy")) ||
                entry.key.startsWith("groovy.runtime.metaclass") ||
                entry.key.startsWith("Script") ||
                entry.key.contains("GStringTemplateScript") ||
                entry.key.contains("SimpleTemplateScript") ||
                entry.key.contains("XmlTemplateScript") ||
                entry.key.contains("StreamingTemplateScript") ||
                entry.key.contains("GeneratedMarkupTemplate")
            ) {
                itt.remove()
                log.trace("Removed class parallelLock: {}", entry.key)
            }
        }
        log.info("Clean parallelLock. left: {}", m.size())
    }
}
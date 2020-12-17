import cn.xnatural.enet.event.EL
import cn.xnatural.enet.event.EP
import cn.xnatural.jpa.Repo
import cn.xnatural.sched.Sched
import core.*
import ctrl.MainCtrl
import ctrl.TestCtrl
import dao.entity.Permission
import dao.entity.Test
import dao.entity.VersionFile
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import service.FileUploader
import service.TestService

@Field final Logger log = LoggerFactory.getLogger(getClass())
@Field final AppContext app = new AppContext() //应用上下文
@Lazy @Field EP ep = app.bean(EP)


// 系统功能添加区
app.addSource(new OkHttpSrv()) // http 客户端
app.addSource(new EhcacheSrv()) // ehcache 封装
app.addSource(new ServerTpl("sched") { // 定时任务
    Sched sched
    @EL(name = "sys.starting", async = true)
    void start() {
        sched = new Sched(attrs(), exec)
        exposeBean(sched)
    }

    @EL(name = "sys.stopping", async = true)
    void stop() { sched?.stop() }
})
app.addSource(new Remoter()) // 集群分布式
app.addSource(new ServerTpl("jpa_local") { //数据库 jpa_local
    Repo repo
    @EL(name = "sys.starting", async = true)
    void start() {
        repo = new Repo(attrs()).entities( // jpa封装
            Test, VersionFile, Permission
        ).init()
        exposeBean(repo)
    }

    @EL(name = "sys.stopping", async = true)
    void stop() { repo?.close() }
})
app.addSource(new HttpSrv().ctrls(
    TestCtrl, MainCtrl
))
app.addSource(new FileUploader())
app.addSource(new TestService())
app.addSource(this)
app.start() // 启动系统


@EL(name = 'sys.inited') //系统初始化完成
void sysInited() {
    if (app.env['redis']) { //根据配置是否有redis,创建redis客户端工具
        app.addSource(new RedisClient())
    }
}


@EL(name = 'sys.started', async = true) //系统启动完成
void sysStarted() {
    try {
//        app.bean(Executor).execute {
//            (1..1000).each {
//                println app.bean(OkHttpSrv).get("http://localhost:7070/test/get?p1=1&p2=xx").execute()
//                Thread.sleep(100 + new Random().nextInt(200))
//            }
//        }
//        app.bean(Executor).execute {
//            (1..1000).each {
//                println app.bean(OkHttpSrv).get("http://localhost:7070/test/async?p1=oo").execute()
//                Thread.sleep(100 + new Random().nextInt(200))
//            }
//        }
//        app.bean(Executor).execute {
//            (1..1000).each {
//                println app.bean(OkHttpSrv).get("http://localhost:7070/test/get?p1=1&p2=xx").execute()
//                Thread.sleep(100 + new Random().nextInt(200))
//            }
//        }
//        app.bean(Executor).execute {
//            (1..1000).each {
//                println app.bean(OkHttpSrv).get("http://localhost:7070/test/async?p1=oo").execute()
//                Thread.sleep(100 + new Random().nextInt(200))
//            }
//        }
//        app.bean(Executor).execute {
//            (1..1000).each {
//                println app.bean(OkHttpSrv).get("http://localhost:7070/test/form?p1=1&p2=xx").execute()
//                Thread.sleep(100 + new Random().nextInt(200))
//            }
//        }
//        app.bean(Executor).execute {
//            (1..1000).each {
//                println app.bean(OkHttpSrv).get("http://localhost:7070/test/async?p1=oo").execute()
//                Thread.sleep(100 + new Random().nextInt(200))
//            }
//        }
        // TODO
    } finally {
        // System.exit(0)
        // ep.fire('sched.after', EC.of(this).args(Duration.ofSeconds(5), {System.exit(0)}).completeFn({ec -> if (ec.noListener) System.exit(0) }))
    }
}
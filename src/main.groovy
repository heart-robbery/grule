import cn.xnatural.enet.event.EL
import cn.xnatural.enet.event.EP
import core.*
import core.http.HttpServer
import core.jpa.HibernateSrv
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
app.addSource(new SchedSrv()) // 定时任务封装 base on quartz库
app.addSource(new Remoter()) // 集群分布式
app.addSource(new HibernateSrv().entities( // jpa封装
    Test, VersionFile, Permission
))
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
        // TODO
    } finally {
        // System.exit(0)
        // ep.fire('sched.after', EC.of(this).args(Duration.ofSeconds(5), {System.exit(0)}).completeFn({ec -> if (ec.noListener) System.exit(0) }))
    }
}
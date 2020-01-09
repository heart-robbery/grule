import cn.xnatural.enet.event.EL
import cn.xnatural.enet.event.EP
import core.AppContext
import core.module.EhcacheSrv
import core.module.OkHttpSrv
import core.module.RedisClient
import core.module.SchedSrv
import core.module.jpa.HibernateSrv
import core.module.remote.Remoter
import ctrl.MainCtrl
import ctrl.TestCtrl
import ctrl.ratpack.RatpackWeb
import dao.entity.Component
import dao.entity.Test
import dao.entity.UploadFile
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import sevice.EmailSrv
import sevice.FileUploader
import sevice.TestService

import javax.annotation.Resource
import java.text.SimpleDateFormat
import java.time.Duration

@Field final Logger log = LoggerFactory.getLogger(getClass())
@Resource @Field EP ep
@Field final AppContext ctx = new AppContext()

// 系统功能添加区
ctx.addSource(new EhcacheSrv())
ctx.addSource(new SchedSrv())
//ctx.addSource(new RedisClient())
ctx.addSource(new OkHttpSrv())
ctx.addSource(new Remoter())
//ctx.addSource(new HibernateSrv().entities(Test, UploadFile, Component))
ctx.addSource(new RatpackWeb().ctrls(TestCtrl, MainCtrl))
ctx.addSource(new EmailSrv())
ctx.addSource(new FileUploader())
ctx.addSource(new TestService())
ctx.addSource(this)
ctx.start() // 启动系统


@EL(name = 'sys.started')
def sysStarted() {
    ctx.bean(SchedSrv)?.after(Duration.ofSeconds(2), {
        log.info ctx.env['redis'].toString()
    })
    return
    TestService ts = ctx.bean(TestService)
    try {
        ts.authTest()

        // cache test
        ep.fire('cache.set', 'test', 'aa', new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').format(new Date()))

        ep.fire('sched.after', Duration.ofSeconds(2), {
            log.info 'cache.get: ' + ep.fire('cache.get', 'test', 'aa')
        })

        ts.hibernateTest()

        ts.okHttpTest()

        // sqlTest()
        // ts.wsClientTest()
    } finally {
        // System.exit(0)
        // ep.fire('sched.after', EC.of(this).args(Duration.ofSeconds(5), {System.exit(0)}).completeFn({ec -> if (ec.noListener) System.exit(0) }))
    }
}
import cn.xnatural.enet.event.EL
import cn.xnatural.enet.event.EP
import core.AppContext
import core.module.EhcacheSrv
import core.module.OkHttpSrv
import core.module.SchedSrv
import core.module.jpa.HibernateSrv
import ctrl.MainCtrl
import ctrl.RuleCtrl
import ctrl.TestCtrl
import ctrl.ratpack.RatpackWeb
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import sevice.FileUploader
import sevice.TestService
import sevice.rule.AttrManager
import sevice.rule.PolicyManger
import sevice.rule.PolicySetManager
import sevice.rule.RuleEngine

import java.text.SimpleDateFormat
import java.time.Duration

@Field final Logger log = LoggerFactory.getLogger(getClass())
@Field final AppContext app = new AppContext()
@Lazy @Field EP ep = app.bean(EP)

// 系统功能添加区
app.addSource(new OkHttpSrv())
app.addSource(new EhcacheSrv())
app.addSource(new SchedSrv())
//app.addSource(new RedisClient())
//app.addSource(new Remoter())
//app.addSource(new HibernateSrv('jpa105'))
app.addSource(new RatpackWeb().ctrls(TestCtrl, MainCtrl, RuleCtrl))
//app.addSource(new RuleSrv())
//app.addSource(new FileUploader())
app.addSource(new TestService())
app.addSource(new AttrManager())
app.addSource(new RuleEngine())
app.addSource(new PolicySetManager())
app.addSource(new PolicyManger())
app.addSource(this)
app.start() // 启动系统


@EL(name = 'sys.started', async = true)
def sysStarted() {
    println(app.bean(OkHttpSrv).get("http://${ep.fire('http.hp')}/risk?policySet=test_ps1").execute())

    TestService ts = app.bean(TestService)
    // ts.taskTest()
    //ts.wsClientTest()
    return
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
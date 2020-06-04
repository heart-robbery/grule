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
import service.TestService
import service.rule.AttrManager
import service.rule.PolicyManger
import service.rule.DecisionManager
import service.rule.DecisionEngine

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
app.addSource(new HibernateSrv('jpa_kratos'))
app.addSource(new RatpackWeb().ctrls(TestCtrl, MainCtrl, RuleCtrl))
//app.addSource(new RuleSrv())
//app.addSource(new FileUploader())
app.addSource(new TestService())
app.addSource(new AttrManager())
app.addSource(new DecisionEngine())
app.addSource(new DecisionManager())
app.addSource(new PolicyManger())
app.addSource(this)
app.start() // 启动系统


@EL(name = 'sys.started', async = true)
def sysStarted() {
//    List<String> ids = "410421199008264512,441721200102232056,320924200005274117,511526199204185610,340403197806161817,433123197704054563,511923200008266673,370126199710101820,532101199901120025,342425198410170113,511324199505254284,420902200104180816,511725200009081111,441302200107145129,362202199811196237,430524200001252459,452528198304027743,321322199409220414,510105199712131278,50010120010321595X,232303199804187044,421083198303054222,331023200002022923,500101196906146223,341021198408277570,320724200107136013,510525199804053438,450703200107072731,370727197310103077,53292319751101051X,220381199511283812,500236199808130235".split(",").toList()
//    app.bean(SchedSrv).after(Duration.ofSeconds(5)) {
//        for (int i = 0; i < 10; i++) {
//            try {
//                // ${ep.fire('http.hp')}
//                // cdh-02.jccfc.local:7070
//                println app.bean(OkHttpSrv).get("http://${ep.fire('http.hp')}/decision?decisionId=desicion"+ (new Random().nextInt(3) + 1))
//                //println app.bean(OkHttpSrv).get("http://cdh-02.jccfc.local:7070/decision?decisionId=desicion" + (new Random().nextInt(3) + 1))
//                    .param('thirdChannelCode', 'test')
//                    .param('thirdOperId', 'test')
//                    .param('thirdAuthNo', '11111111111')
//                    .param('age', 50)
//                //.param('appCode', 'FQL')
//                    .param('queryType', '99')
//                    .param('mobileNo', '18280065906')
//                    .param('idNumber', ids.get(new Random().nextInt(ids.size())))
//                //.debug()
//                    .execute()
//            } catch (ex) {}
//        }
//        log.info("end====================")
//    }

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
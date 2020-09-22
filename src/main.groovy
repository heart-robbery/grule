import cn.xnatural.enet.event.EL
import cn.xnatural.enet.event.EP
import core.*
import core.http.HttpServer
import core.jpa.HibernateSrv
import ctrl.MainCtrl
import ctrl.MntCtrl
import ctrl.RuleCtrl
import ctrl.TestCtrl
import dao.entity.*
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import service.FileUploader
import service.TestService
import service.rule.AttrManager
import service.rule.DecisionEngine
import service.rule.DecisionManager
import service.rule.OpHistorySrv

@Field final Logger log = LoggerFactory.getLogger(getClass())
@Field final AppContext app = new AppContext()
@Lazy @Field EP ep = app.bean(EP)

// 系统功能添加区
app.addSource(new OkHttpSrv())
app.addSource(new EhcacheSrv())
app.addSource(new SchedSrv())
//app.addSource(new RedisClient())
app.addSource(new Remoter())
app.addSource(new HibernateSrv('jpa_rule').entities(
     Decision, RuleField, DataCollector, OpHistory, DecisionResult, CollectResult
))
app.addSource(new HttpServer().ctrls(
    TestCtrl, MainCtrl, RuleCtrl, MntCtrl
))
app.addSource(new FileUploader())
app.addSource(new TestService())
app.addSource(new AttrManager())
app.addSource(new DecisionEngine())
app.addSource(new DecisionManager())
app.addSource(new OpHistorySrv())
// app.addSource(new PolicyManger())
app.addSource(this)
app.start() // 启动系统


@EL(name = 'sys.started', async = true)
void sysStarted() {
    try {
    } finally {
        // System.exit(0)
    }
}
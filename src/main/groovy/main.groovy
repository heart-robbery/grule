import cn.xnatural.enet.event.EL
import cn.xnatural.enet.event.EP
import com.alibaba.fastjson.JSON
import core.AppContext
import core.module.EhcacheSrv
import core.module.OkHttpSrv
import core.module.RedisClient
import core.module.SchedSrv
import core.module.jpa.BaseRepo
import core.module.jpa.HibernateSrv
import ctrl.MainCtrl
import ctrl.TestCtrl
import ctrl.ratpack.RatpackWeb
import dao.entity.Component
import dao.entity.Test
import dao.entity.UploadFile
import groovy.transform.Field
import okhttp3.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import sevice.FileUploader
import sevice.TestService

import javax.annotation.Resource
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Field Logger log = LoggerFactory.getLogger(getClass())
@Resource @Field EP ep
@Resource @Field ExecutorService exec
@Resource @Field OkHttpSrv okHttp
@Field AppContext ctx = new AppContext()


// 系统功能添加区
ctx.addSource(new EhcacheSrv())
ctx.addSource(new SchedSrv())
ctx.addSource(new RedisClient())
ctx.addSource(new OkHttpSrv())
ctx.addSource(new HibernateSrv().entities(Test, UploadFile, Component))
ctx.addSource(new RatpackWeb().ctrls(TestCtrl, MainCtrl))
ctx.addSource(new FileUploader())
ctx.addSource(new TestService())
ctx.addSource(this)
ctx.start() // 启动系统


@EL(name = 'sys.started')
def sysStarted() {
    try {
        // cache test
        ep.fire('cache.set', 'test', 'aa', new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()))

        ep.fire("sched.after", Duration.ofSeconds(2), {
            log.info 'cache.get: ' + ep.fire('cache.get', 'test', 'aa')
        })
        hibernateTest()

        def hp = ep.fire('http.getHp')
        if (hp) {
            // log.info '接口访问xxx: ' + okHttp.http().get("http://$hp/test/xxx").execute()
            log.info '接口访问dao: ' + okHttp.get("http://$hp/test/dao").cookie('sId', '222').param('type', 'file').execute()
            log.info '接口访问form: ' + okHttp.post("http://$hp/test/form?sss=22").param('p1', '111').execute()
            log.info '接口访问json: ' + okHttp.post("http://$hp/test/json").param('p1', '111').execute()
            log.info '接口访问json: ' + okHttp.post("http://$hp/test/json").jsonBody(JSON.toJSONString([a:'1', b:2])).execute()
            // log.info '接口访问upload: ' + okHttp.post("http://$hp/test/upload").param('f1', new File('C:\\Users\\86178\\Pictures\\Screenshots\\屏幕截图(1).png')).execute()
        }

        // sqlTest()
        // wsClientTest()
    } finally {
        // ep.fire('sched.after', EC.of(this).args(Duration.ofSeconds(5), {System.exit(0)}).completeFn({ec -> if (ec.noListener) System.exit(0) }))
    }
}


@EL(name = 'sys.stopping')
def stop() {
    // sql?.close()
}



def wsClientTest() {
    OkHttpClient okClient = ep.fire('bean.get', OkHttpClient.class)
    okClient.newWebSocket(new Request.Builder().url("ws://rl.cnxnu.com:9659/ppf/ar/6.0").build(), new WebSocketListener() {
        @Override
        void onOpen(WebSocket webSocket, Response resp) {
            println "webSocket onOpen: ${resp.body().string()}"
        }
        AtomicInteger i = new AtomicInteger();
        @Override
        void onMessage(WebSocket webSocket, String text) {
            System.out.println("消息" + i.getAndIncrement() + ": " + text);
        }
        @Override
        void onFailure(WebSocket webSocket, Throwable t, Response response) {
            t.printStackTrace()
        }
    });
    Thread.sleep(TimeUnit.MINUTES.toMillis(10))
}


def hibernateTest() {
    TestService ts = ep.fire('bean.get', TestService.class)
    ts.hibernateMap()

    ts.findTestData()
    BaseRepo repo = ep.fire('bean.get', BaseRepo.class)
    println "total: " + repo.count(Test)
}


//def sqlTest() {
//    sql.execute('''
//      create table if not exists test (
//          name varchar(20),
//          age int(10)
//      )
//    ''')
//    sql.executeInsert("insert into test values(?, ?)", ["xxxx" + System.currentTimeMillis(), 1111])
//    println sql.firstRow("select count(*) as num from test").num
//}
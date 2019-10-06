import cn.xnatural.enet.event.EC
import cn.xnatural.enet.event.EL
import cn.xnatural.enet.event.EP
import core.AppContext
import core.module.EhcacheSrv
import core.module.SchedSrv
import core.module.jpa.BaseRepo
import core.module.jpa.HibernateSrv
import ctrl.TestCtrl
import dao.entity.Test
import dao.entity.UploadFile
import groovy.transform.Field
import ctrl.RatpackWeb
import okhttp3.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import sevice.FileUploader
import sevice.TestService

import javax.annotation.Resource
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger


@Field Logger log = LoggerFactory.getLogger(getClass())
@Lazy @Field OkHttpClient okClient = createOkClient()
@Resource @Field EP ep
@Resource @Field ExecutorService exec
@Field AppContext ctx = new AppContext()

// 系统功能添加区
ctx.addSource(new EhcacheSrv())
ctx.addSource(new SchedSrv())
ctx.addSource(new HibernateSrv().entities(Test, UploadFile))
ctx.addSource(new RatpackWeb().ctrls(TestCtrl))
ctx.addSource(new FileUploader())
ctx.addSource(new TestService())
ctx.addSource(this)
ctx.start() // 启动系统


@EL(name = "sys.started")
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
            log.info '接口访问: ' + okClient.newCall(new Request.Builder().get().url("http://$hp/dao").build()).execute().body().string()
        }

        // sqlTest()
        // wsClientTest()
    } finally {
        // ep.fire('sched.after', EC.of(this).args(Duration.ofSeconds(5), {System.exit(0)}).completeFn({ec -> if (ec.noListener) System.exit(0) }))
    }
}


@EL(name = "sys.stopping")
def stop() {
    // sql?.close()
}


@EL(name = 'bean.get', async = false)
def findBean(EC ec, Class bType, String bName) {
    if (ec.result) return ec.result
    if (bType.isAssignableFrom(OkHttpClient.class)) {
        if (okClient) return okClient
        else {
            okClient = createOkClient()
            return okClient
        }
    }
}


def wsClientTest() {
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
    BaseRepo repo = ep.fire('bean.get', BaseRepo.class);
    repo?.trans({ se ->
        repo.saveOrUpdate(new Test(age: 222, name: new Date().toString()))
        log.info "total: " + repo.count(Test.class)
    })
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


OkHttpClient createOkClient() {
    new OkHttpClient.Builder()
            .readTimeout(Duration.ofSeconds(17)).connectTimeout(Duration.ofSeconds(5))
            .dispatcher(new Dispatcher(exec))
            .cookieJar(new CookieJar() {// 共享cookie
                final Map<String, List<Cookie>> cookieStore = new ConcurrentHashMap<>()
                @Override
                void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                    cookieStore.put(url.host(), cookies)
                }
                @Override
                List<Cookie> loadForRequest(HttpUrl url) {
                    List<Cookie> cookies = cookieStore.get(url.host())
                    return cookies != null ? cookies : new ArrayList<>(7)
                }
            }).build()
}
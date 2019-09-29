import cn.xnatural.enet.common.Log
import cn.xnatural.enet.core.AppContext
import cn.xnatural.enet.event.EL
import cn.xnatural.enet.event.EP
import cn.xnatural.enet.server.dao.hibernate.Hibernate
import cn.xnatural.enet.server.session.MemSessionManager
import cn.xnatural.enet.server.session.RedisSessionManager
import dao.entity.Test
import dao.entity.UploadFile
import dao.repo.TestRepo
import dao.repo.UploadFileRepo
import groovy.transform.Field
import module.RatpackWeb
import okhttp3.*
import sevice.FileUploader
import sevice.TestService

import javax.annotation.Resource
import java.time.Duration
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger


new core.AppContext().start()

return

@Field Log log = Log.of(getClass().simpleName)
@Field def ctx = new AppContext()
@Resource @Field Executor exec
@Resource @Field OkHttpClient okClient
@Resource @Field EP ep



// 系统功添加区能
//ctx.addSource(new SchedServer())
ctx.addSource(new Hibernate().entities(Test.class, UploadFile.class).repos(TestRepo.class, UploadFileRepo.class))
ctx.addSource(new RatpackWeb())
ctx.addSource(this)
ctx.start()


@EL(name = "env.configured", async = false)
def envConfigured() {
    if (ctx.env().getBoolean("session.enabled", false)) {
        String t = ctx.env().getString("session.type", "memory");
        // 根据配置来启动用什么session管理
        if ("memory".equalsIgnoreCase(t)) ctx.addSource(new MemSessionManager());
        else if ("redis".equalsIgnoreCase(t)) ctx.addSource(new RedisSessionManager());
    }
    ctx.addSource(new FileUploader())
    ctx.addSource(new TestService())
}


@EL(name = "sys.started")
def sysStarted() {
    try {
        ctx.ep.fire("sched.after", Duration.ofSeconds(2), {
            println 'xxxxxxxxxxxxx'
        })
        // hibernateTest()
        // sqlTest()
//         wsClientTest()
    } finally {
        // ctx.stop()
    }
}


@EL(name = "sys.stopping")
def stop() {
    // sql?.close()
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
    Thread.sleep(TimeUnit.MINUTES.toMillis(10));
}


def hibernateTest() {
    def h = (Hibernate) ctx.ep.fire('bean.get', Hibernate.class)
    h.doWork({se ->
        def e = new Test();
        e.with {
            setAge(222)
            setName(new Date().toString())
        }
        se.saveOrUpdate(e)
        println se.createSQLQuery("select count(*) as num from test").singleResult
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


OkHttpClient okClient() {
    new OkHttpClient.Builder()
            .readTimeout(Duration.ofSeconds(17)).connectTimeout(Duration.ofSeconds(5))
            .dispatcher(new Dispatcher(ctx.ep.fire("bean.get", ExecutorService.class)))
            .cookieJar(new CookieJar() {// 共享cookie
        final HashMap<String, List<Cookie>> cookieStore = new HashMap<>();
        @Override
        void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
            cookieStore.put(url.host(), cookies);
        }
        @Override
        List<Cookie> loadForRequest(HttpUrl url) {
            List<Cookie> cookies = cookieStore.get(url.host());
            return cookies != null ? cookies : new ArrayList<>(2);
        }
    }).build();
}
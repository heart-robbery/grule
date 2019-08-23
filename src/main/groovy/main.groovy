import cn.xnatural.enet.common.Log
import cn.xnatural.enet.core.AppContext
import cn.xnatural.enet.event.EL
import cn.xnatural.enet.event.EP
import groovy.sql.Sql
import groovy.transform.Field
import module.UndertowWebSrv
import okhttp3.*
import sevice.FileUploader

import javax.annotation.Resource
import java.time.Duration
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

//new SqlTest().run()
//return

@Field Log log = Log.of(getClass().simpleName)
@Field def ctx = new AppContext();
@Resource @Field Sql sql
@Resource @Field Executor exec
@Resource @Field OkHttpClient okClient
@Resource @Field EP ep


// 系统功能添加区
//ctx.addSource(new SchedServer())
//ctx.addSource(new Hibernate().scanEntity(Test.class))
//ctx.addSource(new Remoter())
ctx.addSource(new UndertowWebSrv())
ctx.addSource(this)
ctx.start()


@EL(name = "env.configured", async = false)
def envConfigured() {
    ctx.addSource(new FileUploader())
}

@EL(name = "sys.starting")
def sysStarting() {
    // ctx.addSource(new Sql(DruidDataSourceFactory.createDataSource(ctx.env().group("ds"))))
    // ctx.addSource(okClient());
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
    sql?.close()
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
//    def h = (Hibernate) ctx.ep.fire('bean.get', Hibernate.class)
//    h.doWork({se ->
//        def e = new Test();
//        e.setAge(222)
//        e.setName(new Date().toString())
//        se.saveOrUpdate(e)
//        println se.createSQLQuery("select count(*) as num from test").singleResult
//    })
}

def sqlTest() {
    sql.execute('''
      create table if not exists test (
          name varchar(20),
          age int(10)
      )
    ''')
    sql.executeInsert("insert into test values(?, ?)", ["xxxx" + System.currentTimeMillis(), 1111])
    println sql.firstRow("select count(*) as num from test").num
}


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
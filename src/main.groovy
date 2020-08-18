import cn.xnatural.enet.event.EL
import cn.xnatural.enet.event.EP
import core.AppContext
import core.module.*
import core.module.http.HttpServer
import core.module.jpa.HibernateSrv
import ctrl.MainCtrl2
import ctrl.TestCtrl2
import dao.entity.Component
import dao.entity.Test
import dao.entity.VersionFile
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import service.FileUploader
import service.TestService

import java.text.SimpleDateFormat
import java.time.Duration


@Field final Logger log = LoggerFactory.getLogger(getClass())
@Field final AppContext app = new AppContext()
@Lazy @Field EP ep = app.bean(EP)

// 系统功能添加区
app.addSource(new OkHttpSrv())
app.addSource(new EhcacheSrv())
app.addSource(new SchedSrv())
app.addSource(new RedisClient())
app.addSource(new Remoter())
app.addSource(new HibernateSrv().entities(Test, VersionFile, Component))
//app.addSource(new RatpackWeb().ctrls(TestCtrl, MainCtrl))
app.addSource(new HttpServer().ctrls(TestCtrl2, MainCtrl2))
app.addSource(new FileUploader())
app.addSource(new TestService())
app.addSource(this)
app.start() // 启动系统


@EL(name = 'sys.started', async = true)
void sysStarted() {
    return
    app.bean(SchedSrv).dyn({
        log.info("执行 dyn sched " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()))
    }, {
        def cal = Calendar.getInstance()
        cal.add(Calendar.SECOND, 5)
        if (cal.get(Calendar.MINUTE) > 05) return null
        cal.getTime()
    })
//    (1..100).each {i ->
//        (1..2).each {j ->
//            app.bean(OkHttpSrv).get("http://localhost:7070/test/remote?event=eName"+(new Random().nextInt(11) + 1)).debug().execute({s ->
//                log.info("==============" + s)
//            }, null)
//        }
//        log.info "==========" + app.bean(OkHttpSrv).get("http://localhost:7070/test/remote?event=eName"+(new Random().nextInt(11) + 1)).debug().execute()
//    }
//    app.bean(AioServer).msgFn {s ->
//        log.info("收到的消息: " + s)
//        "i received " + System.currentTimeMillis()
//    }
//    def sc = AsynchronousSocketChannel.open(AsynchronousChannelGroup.withThreadPool(app.bean(ExecutorService)))
//    sc.connect(new InetSocketAddress("localhost",8000)).get()
//    for (int i = 0; i < 5; i++) {
//        sc.write(ByteBuffer.wrap("send a msg $i".getBytes("utf-8")))
//    }

    TestService ts = app.bean(TestService)
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
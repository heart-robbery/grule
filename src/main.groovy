import cn.xnatural.enet.event.EL
import cn.xnatural.enet.event.EP
import core.AppContext
import core.module.EhcacheSrv
import core.module.OkHttpSrv
import core.module.RedisClient
import core.module.SchedSrv
import core.module.jpa.HibernateSrv
import ctrl.MainCtrl
import ctrl.TestCtrl
import ctrl.ratpack.RatpackWeb
import dao.entity.Component
import dao.entity.Test
import dao.entity.UploadFile
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import sevice.FileUploader
import sevice.TestService

import javax.annotation.Resource
import java.text.SimpleDateFormat
import java.time.Duration

//def f = new File('e:/tmp/code.txt')
//f.setText('', 'utf-8')
//new File('E:\\code_repo\\锦程消费金融\\thrall\\src\\main\\java\\com\\jccfc\\thrall').eachFileRecurse(FileType.FILES) {
//    if (it.name.endsWith('.java')) {
//        it.eachLine {l ->
//            if (l.trim().isEmpty() || l.containsIgnoreCase('@author') || l.containsIgnoreCase('@create')) return
//            f << l + '\n'
//        }
//        f << '\n\n'
//    }
//}
//return


@Field Logger log = LoggerFactory.getLogger(getClass())
@Resource @Field EP ep
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
    TestService ts = ep.fire('bean.get', TestService.class)
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


@EL(name = 'sys.stopping')
def stop() {
    // sql?.close()
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
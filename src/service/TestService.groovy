package service

import cn.xnatural.app.ServerTpl
import cn.xnatural.enet.event.EL
import cn.xnatural.jpa.Page
import cn.xnatural.jpa.Repo
import cn.xnatural.remoter.Remoter
import cn.xnatural.task.TaskContext
import cn.xnatural.task.TaskWrapper
import com.alibaba.fastjson.JSON
import core.OkHttpSrv
import core.mode.builder.ObjBuilder
import core.mode.pipeline.Pipeline
import core.mode.v.VChain
import core.mode.v.VProcessor
import entity.Test

import java.text.SimpleDateFormat
import java.util.function.Consumer

class TestService extends ServerTpl {
    @Lazy def repo = bean(Repo)
    @Lazy def http = bean(OkHttpSrv)


    @EL(name = 'sys.heartbeat', async = true)
    void timeNotify() {
        // 向测试 web socket 每分钟发送消息
        ep.fire("testWsMsg", '系统时间: ' + new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').format(new Date()))
    }


    Page findTestData() {
        repo.trans{ s ->
            repo.saveOrUpdate(
                new Test(
                    name: new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').format(new Date()),
                    age: Integer.valueOf(new SimpleDateFormat('ss').format(new Date()))
                )
            )
            repo.findPage(Test, 1, 10, { root, query, cb -> query.orderBy(cb.desc(root.get('createTime')))})
        }
    }


    def hibernateTest() {
        repo?.trans{s ->
            println '============='
//            println s.createNativeQuery('select name, age from test where age>10')
//                .setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP)
//                .setMaxResults(1).uniqueResult()
//            println s.createQuery('select name,age from Test where age>10')
//                // .setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP)
//                .setMaxResults(1).singleResult['age']
            println s.createQuery('from Test where age>10').setMaxResults(1).list()
        }

        findTestData()
        println "total: " + repo.count(Test)
    }


    def wsClientTest() {
        def i = 1
        http.ws('ws://localhost:7100/test/ws', 90,{msg, ws ->
            log.info("消息" + (i++) + ": " + msg)
        })
    }


    def okHttpTest() {
        def hp = ep.fire('http.hp')
        // hp = '39.104.28.131/gy'
        if (hp) {
            // log.info '接口访问xxx: ' + okHttp.http().get("http://$hp/test/xxx").execute()
            log.info '接口访问dao: ' + http.get("http://$hp/test/dao").cookie('sId', '222').param('type', 'file').execute()
            log.info '接口访问form: ' + http.post("http://$hp/test/form?sss=22").param('p1', '中文').execute()
            log.info '接口访问json: ' + http.post("http://$hp/test/json").contentType('application/json').param('p1', '中文 123').execute()
            log.info '接口访问json: ' + http.post("http://$hp/test/json").jsonBody(JSON.toJSONString([a:'1', b:2])).execute()
            http.post("http://$hp/test/upload?cus=11111").param('p2', 'abc 怎么')
                .param('f1', new File('C:\\Users\\xiangxb\\Desktop\\新建文本文档.txt'))
                .execute({log.info '接口访问upload: ' + it})
        }
    }


    // 权限测试
    def authTest() {
        def hp = ep.fire('http.hp')
        if (hp) {
            log.info("testLogin(admin): " + http.get("http://$hp/test/testLogin?role=admin").execute())
            log.info("auth(admin): " + http.get("http://$hp/test/auth?role=admin").execute())

            log.info("testLogin(admin): " + http.get("http://$hp/test/testLogin?role=admin").execute())
            log.info("auth(login): " + http.get("http://$hp/test/auth?role=login").execute())

            log.info("testLogin(role3): " + http.get("http://$hp/test/testLogin?role=role3").execute())
            log.info("auth(role2_1_1): " + http.get("http://$hp/test/auth?role=role2_1_1").execute())

            log.info("testLogin(role4): " + http.get("http://$hp/test/testLogin?role=role4").execute())
            log.info("auth(role4_1_1_1): " + http.get("http://$hp/test/auth?role=role4_1_1_1").execute())

            log.info("testLogin(admin): " + http.get("http://$hp/test/testLogin?role=admin").execute())
            log.info("auth(未知角色名): " + http.get("http://$hp/test/auth?role=未知角色名").execute())

            log.info("testLogin(admin): " + http.get("http://$hp/test/testLogin?role=admin").execute())
            log.info("auth(role2): " + http.get("http://$hp/test/auth?role=role2").execute())

            log.info("testLogin(role2_1): " + http.get("http://$hp/test/testLogin?role=role2_1").execute())
            log.info("auth(role2_1_1): " + http.get("http://$hp/test/auth?role=role2_1_1").execute())

            log.info("testLogin(): " + http.get("http://$hp/test/testLogin?role=").execute())
            log.info("auth(admin): " + http.get("http://$hp/test/auth?role=admin").execute())
        }
    }


    @EL(name = "eName11")
    void taskTest() {
        new TaskContext<>('test ctx', null, exec())
            .addTask(new TaskWrapper().step {param, me -> me.info("执行任务....")})
            .addTask(new TaskWrapper().step {param, me ->
                me.info("执行任务")
            }.step { param, me ->
                me.task().ctx().addTask(new TaskWrapper().step{param1, mee -> mee.info("执行衍生任务....")})
            })
            .start()
    }


    @EL(name = "eName10")
    def testObjBuilder() {
        println ObjBuilder.of(Map).add("a", {"b"}).build()
    }


    @EL(name = "eName9")
    def testPipe() {
        println new Pipeline(key: 'test pipe').add({ i -> i + "xxx"}).run("qqq")
    }


    @EL(name = "eName8")
    def testVChain() {
        new VChain().add(new VProcessor() {
            @Override
            def down(Map ctx) {
                log.info('down1')
            }

            @Override
            def up(Map ctx) {
                log.info('up1')
            }
        }).add(new VProcessor() {
            @Override
            def down(Map ctx) {
                log.info('down2')
            }

            @Override
            def up(Map ctx) {
                log.info('up2')
            }
        }).run()
    }


    def remote(String app, String eName, String param = 'xx', Consumer fn) {
        // 远程调用
        fn.accept(bean(Remoter).fire(app?:'gy', eName?:'eName1', ['p1']))
        // bean(Remoter).fireAsync(app?:'gy', eName?:'eName1', fn, [])
    }


    @EL(name = "eName1", async = false)
    private String testEvent1(String p) {
        Test e = new Test()
        e.setName("aaaa" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        e.setAge(111)
        Test t = repo.saveOrUpdate(e);
        // if (true) throw new IllegalArgumentException("xxx");
        return "new entity : " + t.getId()
    }


    @EL(name = "eName2")
    def testEvent2(String p) {
        repo.findPage(Test, 1, 10, { root, query, cb -> query.orderBy(cb.desc(root.get("id")))})
    }


    @EL(name = "eName3")
    long testEvent3(String p) {
        repo.count(Test, {root, query, cb -> query.orderBy(cb.desc(root.get("id")))})
    }


    @EL(name = "eName4")
    def testEvent4(String p) {
        ep.fire("cache.get","java","java")
    }


    @EL(name = "eName5")
    void testEvent5(String p) {
        ep.fire("cache.set","java","java", p?:(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())))
    }

    @EL(name = "eName6")
    def testEvent6(String p) {
        throw new RuntimeException("抛个错 $p")
    }

    @EL(name = "eName7")
    def testEvent7(String i) {
        Thread.sleep(5000L)
        "eName7_"+System.currentTimeMillis()
    }
}

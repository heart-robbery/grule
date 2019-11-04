package sevice

import cn.xnatural.enet.event.EC
import cn.xnatural.enet.event.EL
import com.alibaba.fastjson.JSON
import core.AppContext
import core.Page
import core.module.OkHttpSrv
import core.module.ServerTpl
import core.module.jpa.BaseRepo
import ctrl.common.FileData
import dao.entity.Test
import dao.entity.UploadFile
import groovy.transform.Field
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.hibernate.transform.Transformers

import javax.annotation.Resource
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

class TestService extends ServerTpl {
    @Resource
    BaseRepo  repo
    @Resource
    OkHttpSrv okHttp


    Page findTestData() {
        repo?.trans{ s ->
            repo.saveOrUpdate(
                new Test(
                    name: new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').format(new Date()),
                    age: Integer.valueOf(new SimpleDateFormat('ss').format(new Date()))
                )
            )
            repo.findPage(Test, 0, 10, { root, query, cb -> query.orderBy(cb.desc(root.get('createTime')))})
        }
    }


    /**
     * 记录上传的文件
     * @param files
     * @return
     */
    List<UploadFile> saveUpload(List<FileData> files) {
        if (!files) return Collections.emptyList()
        repo?.trans{
            files.collect{f ->
                repo.saveOrUpdate(new UploadFile(originName: f.originName, finalName: f.generatedName))
            }
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
        okHttp.client().newWebSocket(new Request.Builder().url("ws://rl.cnxnu.com:9659/ppf/ar/6.0").build(), new WebSocketListener() {
            @Override
            void onOpen(WebSocket webSocket, Response resp) {
                println "webSocket onOpen: ${resp.body().string()}"
            }
            final AtomicInteger i = new AtomicInteger()
            @Override
            void onMessage(WebSocket webSocket, String text) {
                println("消息" + i.getAndIncrement() + ": " + text)
            }
            @Override
            void onFailure(WebSocket webSocket, Throwable t, Response resp) {
                t.printStackTrace()
            }
        })
    }


    def okHttpTest() {
        def hp = ep.fire('http.hp')
        // hp = '39.104.28.131/gy'
        if (hp) {
            // log.info '接口访问xxx: ' + okHttp.http().get("http://$hp/test/xxx").execute()
            log.info '接口访问dao: ' + okHttp.get("http://$hp/test/dao").cookie('sId', '222').param('type', 'file').execute()
            log.info '接口访问form: ' + okHttp.post("http://$hp/test/form?sss=22").param('p1', '中文').execute()
            log.info '接口访问json: ' + okHttp.post("http://$hp/test/json").contentType('application/json').param('p1', '中文 123').execute()
            log.info '接口访问json: ' + okHttp.post("http://$hp/test/json").jsonBody(JSON.toJSONString([a:'1', b:2])).execute()
            okHttp.post("http://$hp/test/upload?cus=11111").param('p2', 'abc 怎么')
                .param('f1', new File('C:\\Users\\xiangxb\\Desktop\\新建文本文档.txt'))
                .execute({log.info '接口访问upload: ' + it})
        }
    }


    // 权限测试
    def authTest() {
        def hp = ep.fire('http.hp')
        if (hp) {
            log.info("testLogin(admin): " + okHttp.get("http://$hp/test/testLogin?role=admin").execute())
            log.info("auth(admin): " + okHttp.get("http://$hp/test/auth?role=admin").execute())

            log.info("testLogin(admin): " + okHttp.get("http://$hp/test/testLogin?role=admin").execute())
            log.info("auth(login): " + okHttp.get("http://$hp/test/auth?role=login").execute())

            log.info("testLogin(role3): " + okHttp.get("http://$hp/test/testLogin?role=role3").execute())
            log.info("auth(role2_1_1): " + okHttp.get("http://$hp/test/auth?role=role2_1_1").execute())

            log.info("testLogin(role4): " + okHttp.get("http://$hp/test/testLogin?role=role4").execute())
            log.info("auth(role4_1_1_1): " + okHttp.get("http://$hp/test/auth?role=role4_1_1_1").execute())

            log.info("testLogin(admin): " + okHttp.get("http://$hp/test/testLogin?role=admin").execute())
            log.info("auth(未知角色名): " + okHttp.get("http://$hp/test/auth?role=未知角色名").execute())

            log.info("testLogin(admin): " + okHttp.get("http://$hp/test/testLogin?role=admin").execute())
            log.info("auth(role2): " + okHttp.get("http://$hp/test/auth?role=role2").execute())

            log.info("testLogin(role2_1): " + okHttp.get("http://$hp/test/testLogin?role=role2_1").execute())
            log.info("auth(role2_1_1): " + okHttp.get("http://$hp/test/auth?role=role2_1_1").execute())

            log.info("testLogin(): " + okHttp.get("http://$hp/test/testLogin?role=").execute())
            log.info("auth(admin): " + okHttp.get("http://$hp/test/auth?role=admin").execute())
        }
    }


    void remote(String app, String eName, String ret, Consumer fn) {
        // 远程调用
        ep.fire("remote", EC.of(this).args(app, eName, [ret]).completeFn({ec ->
            if (ec.isSuccess()) fn.accept(ec.result);
            else fn.accept(ec.ex() == null ? new Exception(ec.failDesc()) : ec.ex());
        }))
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


    @EL(name = "eName2", async = false)
    private Object testEvent2(String p) {
        return repo.findPage(0, 20, { root, query, cb -> query.orderBy(cb.desc(root.get("id")))})
    }


    @EL(name = "eName3", async = false)
    private long testEvent3(String p) {
        return repo.count({root, query, cb -> query.orderBy(cb.desc(root.get("id")))})
    }


    @EL(name = "eName4", async = false)
    private Object testEvent4(String p) {
        return ep.fire("cache.get","java","java")
    }


    @EL(name = "eName5", async = false)
    private void testEvent5(String p) {
        ep.fire("cache.set","java","java", p)
    }
}
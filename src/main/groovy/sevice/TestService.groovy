package sevice

import cn.xnatural.enet.event.EC
import cn.xnatural.enet.event.EL
import core.module.ServerTpl
import core.module.jpa.BaseRepo
import core.module.jpa.Page
import ctrl.common.FileData
import dao.entity.Test
import dao.entity.UploadFile

import javax.annotation.Resource
import java.text.SimpleDateFormat
import java.util.function.Consumer

class TestService extends ServerTpl {
    @Resource
    BaseRepo repo


    Page findTestData() {
        repo.trans{ s ->
            repo.saveOrUpdate(
                new Test(
                    name: new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()),
                    age: Integer.valueOf(new SimpleDateFormat("ss").format(new Date()))
                )
            )
            repo.findPage(Test, 0, 10, { root, query, cb -> query.orderBy(cb.desc(root.get("id")))})
        }
    }


    /**
     * 记录上传的文件
     * @param files
     * @return
     */
    List<UploadFile> saveUpload(List<FileData> files) {
        if (!files) return Collections.emptyList()
        repo.trans{s ->
            files.collect{f ->
                repo.saveOrUpdate(new UploadFile(originName: f.originName, finalName: f.generatedName))
            }
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

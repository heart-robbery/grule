package sevice

import cn.xnatural.enet.event.EC
import cn.xnatural.enet.event.EL
import cn.xnatural.enet.server.ServerTpl
import cn.xnatural.enet.server.dao.hibernate.Trans
import ctrl.common.PageModel
import ctrl.request.AddFileDto
import dao.entity.Test
import dao.entity.UploadFile
import dao.repo.TestRepo
import dao.repo.UploadFileRepo

import javax.annotation.Resource
import java.text.SimpleDateFormat
import java.util.function.Consumer

class TestService extends ServerTpl {
    @Resource
    TestRepo testRepo;
    @Resource
    UploadFileRepo uploadFileRepo;


    // @Monitor(trace = true)
    @Trans
    PageModel findTestData() {
        Test e = new Test();
        e.setName("aaaa" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        e.setAge(111);
        testRepo.saveOrUpdate(e);
        // if (true) throw new IllegalArgumentException("xxx");
        return PageModel.of(
            testRepo.findPage(0, 5, {root, query, cb -> query.orderBy(cb.desc(root.get("id"))); return null;}),
            {ee -> ee}
        );
    }


    @Trans
    void save(AddFileDto dto) {
        UploadFile e = new UploadFile();
        e.setOriginName(dto.getHeadportrait().getOriginName());
        e.setThirdFileId(dto.getHeadportrait().getResultName());
        uploadFileRepo.saveOrUpdate(e);
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
        Test e = new Test();
        e.setName("aaaa" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        e.setAge(111);
        Test t = testRepo.saveOrUpdate(e);
        // if (true) throw new IllegalArgumentException("xxx");
        return "new entity : " + t.getId();
    }


    @EL(name = "eName2", async = false)
    private Object testEvent2(String p) {
        return testRepo.findPage(0, 20, {root, query, cb -> query.orderBy(cb.desc(root.get("id"))); return null;});
    }


    @EL(name = "eName3", async = false)
    private long testEvent3(String p) {
        return testRepo.count({root, query, cb -> query.orderBy(cb.desc(root.get("id"))); return null;});
    }


    @EL(name = "eName4", async = false)
    private Object testEvent4(String p) {
        return ep.fire("cache.get","java","java");
    }


    @EL(name = "eName5", async = false)
    private void testEvent5(String p) {
        ep.fire("cache.set","java","java", p);
    }
}

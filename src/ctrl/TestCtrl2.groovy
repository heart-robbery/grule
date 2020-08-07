package ctrl

import cn.xnatural.enet.event.EL
import core.Page
import core.Utils
import core.module.OkHttpSrv
import core.module.ServerTpl
import core.module.aio.AioClient
import core.module.aio.AioServer
import core.module.http.HttpContext
import core.module.http.mvc.*
import core.module.http.ws.Listener
import core.module.http.ws.WS
import core.module.http.ws.WebSocket
import core.module.jpa.BaseRepo
import dao.entity.Test
import dao.entity.UploadFile
import dao.entity.VersionFile
import org.hibernate.query.internal.NativeQueryImpl
import org.hibernate.transform.Transformers
import service.FileUploader
import service.TestService

import java.text.SimpleDateFormat
import java.util.concurrent.ConcurrentHashMap

import static core.module.http.mvc.ApiResp.fail
import static core.module.http.mvc.ApiResp.ok

@Ctrl(prefix = 'test')
class TestCtrl2 extends ServerTpl {
    @Lazy def            ts   = bean(TestService)
    @Lazy def            repo = bean(BaseRepo)
    @Lazy def            fu   = bean(FileUploader)
    @Lazy def            http = bean(OkHttpSrv)

    final Set<WebSocket> wss = ConcurrentHashMap.newKeySet()


    @Filter(order = 1f)
    void filter1(HttpContext ctx) {
        log.info('filter1 ============')
    }

    @Filter(order = 0f)
    void filter2(HttpContext ctx) {
        log.info('filter2 ============')
    }


    @EL(name = 'testWsMsg')
    void wsMsgBroadcast(String msg) {
        wss.each {ws -> ws.send(msg)}
    }


    @WS(path = 'msg')
    void wsMsg(WebSocket ws) {
        log.info('WS connect. {}', ws.session.sc.remoteAddress)
        wss.add(ws)
        ws.listen(new Listener() {

            @Override
            void onClose(WebSocket wst) {
                wss.remove(wst)
            }

            @Override
            void onText(String msg) {
                log.info('test ws receive client msg: {}', msg)
            }
        })
        wsMsgBroadcast(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()))
    }


    // 测试抛出错误
    @Path(path = 'error')
    def error() {
        throw new RuntimeException('错误测试')
    }


    // dao 测试
    @Path(path = 'dao')
    ApiResp dao(String type) {
        if ('file' == type) {
            return ok(Page.of(
                repo.findPage(VersionFile, 1, 10, { root, query, cb -> query.orderBy(cb.desc(root.get('id')))}),
                {UploadFile e ->
                    Utils.toMapper(e).ignore("updateTime")
                        .addConverter("createTime", {Date d -> d?.getTime()}).build()
                }
            ))
        } else {
            return ok(ts?.findTestData())
        }
        return

        // 转换成map
        repo.trans{s ->
            s.createNativeQuery("SELECT * from test")
                .unwrap(NativeQueryImpl).setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP).list()
        }.collect {
            Utils.toMapper(it).ignore("update_time")
                .addConverter("create_time", {Date d -> d?.getTime()}).build()
        }

        // 非数字比较大小(时间大小比较)
        repo.find(Test, { root, query, cb -> cb.greaterThanOrEqualTo(root.get("createTime"), new Date())})
    }


    // 接收form 表单提交
    @Path(path = 'form')
    ApiResp form(HttpContext ctx) {
        ok(ctx.request.formParams)
    }


    // json 参数
    @Path(path = 'json')
    ApiResp json(HttpContext ctx) {
        ok(ctx.request.jsonParams)
    }


    // 接收post string
    @Path(path = 'string')
    ApiResp string(HttpContext ctx) {
        ok(ctx.request.bodyStr)
    }


    // 文件上传
    @Path(path = 'upload')
    ApiResp upload(FileData file, String version) {
        if (file == null) {throw new IllegalArgumentException('文件未上传')}
        fu.save(file)
        repo.saveOrUpdate(new VersionFile(version: version, finalName: file.finalName, originName: file.originName, size: file.size))
        ok(fu.toFullUrl(file.finalName))
    }


    // 异步响应(手动ctx.render)
    @Path(path = 'async')
    void async(String p1, HttpContext ctx) {
        ctx.render(
            ok('p1: ' + p1 + ", " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()))
        )
    }


    // 远程事件调用
    @Path(path = 'remote')
    void remote(HttpContext ctx, String app, String event, String p1) {
        ts.remote(app?:"gy", event?:'eName1', p1?:'p1',{
            if (it instanceof Exception) ctx.render(fail(it.message))
            else ctx.render ok(it)
        })
    }


    // 远程http调用
    @Path(path = 'http')
    ApiResp http(String url) {
        ok(http.get(url?:'http://gy/test/cus').debug().execute())
    }


    // aio 测试发送
    @Path(path = 'aio')
    ApiResp aio(String host, Integer port, String msg) {
        bean(AioClient).send(
            host?:'localhost', port?:bean(AioServer).port,
            msg?:("send " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()))
        )
        ok()
    }


    //故意超时接口
    @Path(path = 'timeout')
    ApiResp timeout(Integer timeout) {
        def t = (timeout?:10)
        Thread.sleep(t * 1000L)
        ok().desc("超时: ${t}s" )
    }


    @Path(path = 'cus')
    ApiResp cus(String p1) {
        log.info("here ================")
        return ok('p1: ' + p1 + ", " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()))
    }
}

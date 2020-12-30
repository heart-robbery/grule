package ctrl

import cn.xnatural.aio.AioClient
import cn.xnatural.aio.AioServer
import cn.xnatural.enet.event.EL
import cn.xnatural.http.*
import cn.xnatural.jpa.Page
import cn.xnatural.jpa.Repo
import cn.xnatural.sched.Sched
import core.OkHttpSrv
import core.ServerTpl
import core.Utils
import dao.entity.Permission
import dao.entity.Test
import dao.entity.UploadFile
import dao.entity.VersionFile
import org.hibernate.query.internal.NativeQueryImpl
import org.hibernate.transform.Transformers
import service.FileUploader
import service.TestService

import java.text.SimpleDateFormat
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

import static cn.xnatural.http.ApiResp.fail
import static cn.xnatural.http.ApiResp.ok

@Ctrl(prefix = 'test')
class TestCtrl extends ServerTpl {
    @Lazy def            ts   = bean(TestService)
    @Lazy def            repo = bean(Repo)
    @Lazy def            fu   = bean(FileUploader)
    @Lazy def            http = bean(OkHttpSrv)

    final Set<WebSocket> wss = ConcurrentHashMap.newKeySet()


    @Filter(order = 1)
    void filter1(HttpContext ctx) {
        log.info('test filter1 ============')
    }

    @Filter(order = 0)
    void filter2(HttpContext ctx) {
        // log.info('test filter2 ============')
    }


    @EL(name = 'testWsMsg')
    void wsMsgBroadcast(String msg) {
        wss.each {ws -> ws.send(msg)}
    }


    @WS(path = 'msg')
    void wsMsg(WebSocket ws) {
        log.info('WS connect. {}', ws.session.remoteAddress)
        ws.listen(new WsListener() {

            @Override
            void onClose(WebSocket wst) {
                wss.remove(wst)
            }

            @Override
            void onText(String msg) {
                log.info('test ws receive client msg: {}', msg)
            }
        })
        wsMsgBroadcast('上线: ' + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()))
        wss.add(ws)
    }


    // 测试抛出错误
    @Path(path = 'error', produce = "application/json")
    def error() {
        throw new Exception('错误测试')
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
    @Path(path = 'form', consumer = 'application/x-www-form-urlencoded')
    ApiResp form(String param1, HttpContext ctx) {
        ok(ctx.request.formParams)
    }


    // json 参数
    @Path(path = 'json', consumer = 'application/json')
    ApiResp json(String param1, HttpContext ctx) {
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
        if (file == null) return ApiResp.fail('文件未上传')
        fu.save(file)
        repo.saveOrUpdate(new VersionFile(version: version, finalName: file.finalName, originName: file.originName, size: file.size))
        ok(fu.toFullUrl(file.finalName))
    }



    @Lazy protected Map<String, File> tmpFiles = new ConcurrentHashMap<>();
    /**
     * 文件上传: 断点续传/持续上传/分片上传
     * 原理: 把文件分成多块 依次上传, 用同一个标识多次上传为同一个文件
     * 多线程上传不安全
     * @param server {@link HttpServer}
     * @param filePiece 文件片
     * @param uploadId 上传id, 用于集合所有分片
     * @param originName 文件原名
     * @param totalPiece 总片数
     * @param currentPiece 当前第几片
     * @return
     */
    @Path(path = "pieceUpload")
    ApiResp pieceUpload(HttpServer server, FileData filePiece, String uploadId, String originName, Integer totalPiece, Integer currentPiece) throws Exception {
        if (filePiece == null) {return ApiResp.fail("文件片未上传");}
        if (uploadId == null || uploadId.isEmpty()) {return ApiResp.fail("Param: uploadId 不能为空");}
        if (totalPiece == null) {return ApiResp.fail("Param: totalPiece 不能为空");}
        if (totalPiece < 2) {return ApiResp.fail("Param: totalPiece >= 2");}
        if (currentPiece == null) {return ApiResp.fail("Param: currentPiece 不能为空");}
        if ((originName == null || originName.isEmpty()) && currentPiece == 1) {return ApiResp.fail("参数错误: originName 不能为空");}
        if (currentPiece < 1) {return ApiResp.fail("Param: currentPiece >= 1");}
        if (totalPiece < currentPiece) {return ApiResp.fail("Param: totalPiece >= currentPiece");}

        ApiResp<Map<String, Object>> resp = ok().attr("uploadId", uploadId).attr("currentPiece", currentPiece);
        if (currentPiece == 1) { // 第一个分片: 保存文件
            tmpFiles.put(uploadId, filePiece.getFile());
            // TODO 过一段时间还没上传完 则主动删除 server.getInteger("pieceUpload.maxKeep", 120)
        } else if (totalPiece > currentPiece) { // 后面的分片: 追加到第一个分片的文件里面去
            File file = tmpFiles.get(uploadId);
            if (file == null) return ApiResp.of("404", "文件未找到: " + uploadId).attr("originName", originName);
            filePiece.appendTo(file); filePiece.delete();

            long maxSize = server.getLong("pieceUpload.maxFileSize", 1024 * 1024 * 200L); // 最大上传200M
            if (file.length() > maxSize) { // 文件大小验证
                file = tmpFiles.remove(uploadId);
                file.delete();
                return ApiResp.fail("上传文件太大, <=" + maxSize);
            }
        } else { // 最后一个分片
            File file = tmpFiles.remove(uploadId);
            filePiece.appendTo(file); filePiece.delete();

            FileData fd = new FileData().setOriginName(originName).setFile(file)
            fu.save(fd)
            return resp.attr("finalName", fd.getFinalName()).attr("url", fu.toFullUrl(fd.finalName));
        }
        return resp;
    }


    // 异步响应(手动ctx.render)
    @Path(path = 'async')
    void async(String p1, HttpContext ctx) {
        ctx.render(
            ok('p1: ' + p1 + ", " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()))
        )
    }


    // 测试登录
    @Path(path = 'login')
    ApiResp login(String username, HttpContext hCtx) {
        hCtx.setSessionAttr('permissions', repo.findList(Permission).collect {it.enName}.toSet())
        ok(hCtx.getSessionAttr("permissions"))
    }


    // 权限测试
    @Path(path = 'auth')
    ApiResp auth(String auth, HttpContext ctx) {
        ok(ctx.auth(auth?:'auth1'))
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


    // 下载excel文件
    void downXlsx(HttpContext ctx) {
        ctx.response.contentType('application/vnd.ms-excel;charset=utf-8')
        ctx.response.header('Content-Disposition', "attachment;filename=${new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())}.xlsx")
        def wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(true)
        def bos = new ByteArrayOutputStream(); wb.write(bos)
        ctx.render(bos.toByteArray())
    }


    @Path(path = 'cus')
    ApiResp cus(String p1) {
        log.info("here ================")
        return ok('p1: ' + p1 + ", " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()))
    }
}

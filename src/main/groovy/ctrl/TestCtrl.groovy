package ctrl

import core.module.jpa.BaseRepo
import core.Page
import ctrl.common.FileData
import dao.entity.UploadFile
import io.netty.handler.codec.http.HttpResponseStatus
import ratpack.exec.Promise
import ratpack.form.Form
import ratpack.handling.Chain
import ratpack.handling.RequestId
import ratpack.websocket.WebSocket
import ratpack.websocket.WebSocketClose
import ratpack.websocket.WebSocketHandler
import ratpack.websocket.WebSocketMessage
import ratpack.websocket.WebSockets
import sevice.FileUploader
import sevice.TestService

import java.text.SimpleDateFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

import static ctrl.common.ApiResp.ok

class TestCtrl extends CtrlTpl {

    TestCtrl() { prefix = 'test' }


    @Lazy exec = bean(Executor)
    @Lazy repo = bean(BaseRepo)


    // 预处理
    def all(Chain chain) {
        chain.all{ctx ->
            // TODO 预处理 #prefix 前缀开头的 接口
            // println "pre process start with $prefix request"
            ctx.next()
        }
    }


    // 测试抛出错误
    def error(Chain chain) {
        chain.get('error') {ctx -> throw new RuntimeException('错误测试') }
    }


    // dao 测试
    def dao(Chain chain) {
        def srv = bean(TestService)
        chain.get('dao') {ctx ->
            if ('file' == ctx.request.queryParams.type) {
                ctx.render ok(Page.of(
                    repo.findPage(UploadFile, 0, 10, { root, query, cb -> query.orderBy(cb.desc(root.get('id')))}),
                    {UploadFile e -> [originName: e.originName, finalName: e.finalName, id: e.id] }
                ))
            } else {
                ctx.render ok(srv?.findTestData())
            }
        }
    }


    // session 测试
    def session(Chain chain) {
        chain.get('session') {ctx ->
            ctx?.sData.lastReqId = ctx.get(RequestId.TYPE).toString()
            ctx.render ok([id:ctx?.sData.id])
        }
    }


    // websocket
    def ws(Chain chain) {
        Set<WebSocket> wss = ConcurrentHashMap.newKeySet()
        chain.get('ws') {ctx ->
            WebSockets.websocket(ctx, new WebSocketHandler<WebSocket>() {
                @Override
                WebSocket onOpen(WebSocket ws) throws Exception {
                    log.info('ws connect. {}', ctx.request.remoteAddress)
                    wss.add(ws)
                    return ws
                }

                @Override
                void onClose(WebSocketClose<WebSocket> close) throws Exception {
                    close.getOpenResult().close()
                    wss.remove(close.getOpenResult())
                    log.info('ws closed. {}', ctx.request.remoteAddress)
                }

                @Override
                void onMessage(WebSocketMessage<WebSocket> frame) throws Exception {
                    log.info('receive ws msg: {}', frame.text)
                }
            })
        }
        ep.fire('sched.cron', '0/30 * * * * ?', {
            wss.each {ws ->
                ws.send(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()))
            }
        })
    }


    // 接收form 表单提交
    def form(Chain chain) {
        chain.post('form', {ctx ->
            ctx.parse(Form.class).then({ form ->
                // form.file('').fileName // 提取上传的文件
                ctx.render ok(form.values())
            })
        })
    }


    // 文件上传
    def upload(Chain chain) {
        def fu = bean(FileUploader)
        def testSrv = bean(TestService)
        chain.post('upload', {ctx ->
            if (!ctx.request.contentType.type.contains('multipart/form-data')) {
                ctx.clientError(HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE.code())
                return
            }
            ctx.parse(Form.class).then({form ->
                def ls = testSrv.saveUpload(
                    fu.save(form.files().values().collect{f -> new FileData(originName: f.fileName, inputStream: f.inputStream)})
                )
                // 返回上的文件的访问地址
                ctx.render ok(ls.collect{fu.toFullUrl(it.finalName)})
            })
        })
    }


    // json 参数
    def json(Chain chain) {
        chain.post('json') {ctx ->
            if (!ctx.request.contentType.type.contains('application/json')) {
                ctx.clientError(HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE.code())
                return
            }
            ctx.parse(Map.class).then({ m ->
                ctx.render ok(m)
            })
        }
    }


    // 依次从外往里执行多个handler, 例: pre/sub
    def pre(Chain chain) {
        chain.prefix('pre') {ch ->
            ch.with {
                all({ctx ->
                    println('xxxxxxxxxxxxxxxx')
                    ctx.next()
                })
                get('sub', { ctx -> ctx.render 'pre/sub' })
                get('sub2', { ctx -> ctx.render 'pre/sub2' })
            }
        }
    }


    // 下载excel文件
    def downXlsx(Chain chain) {
        chain.post('downXlsx') {ctx ->
            ctx.response.contentType('application/vnd.ms-excel;charset=utf-8')
            ctx.header('Content-Disposition', "attachment;filename=${new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())}.xlsx")
            def wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(true)
            def bos = new ByteArrayOutputStream(); wb.write(bos)
            ctx.response.send(bos.toByteArray())
            // wb.write(ctx.response)
        }
    }


    // 异步处理
    def async(Chain chain) {
        chain.get('async') {ctx ->
            ctx.render Promise.async{down ->
                exec.execute{
                    Thread.sleep(3000)
                    down.success(ok('date', new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())))
                }
            }
        }
    }


    // 测试登录
    def testLogin(Chain chain) {
        chain.get('testLogin') {ctx ->
            ctx.sData.uRoles = ctx.request.queryParams.role
            log.warn('用户权限角色被改变')
            ctx.render ok(ctx.sData)
        }
    }


    // 权限测试
    def auth(Chain chain) {
        chain.get('auth') {ctx ->
            ctx.auth('role1')
            ctx.render ok(ctx.sData)
        }
    }
}

package ctrl

import core.module.jpa.BaseRepo
import core.module.jpa.Page
import ctrl.common.FileData
import dao.entity.UploadFile
import io.netty.handler.codec.http.HttpResponseStatus
import ratpack.form.Form
import ratpack.handling.Chain
import sevice.FileUploader
import sevice.TestService

import static ctrl.common.ApiResp.ok

class TestCtrl extends CtrlTpl {


    // 主页
    def index(Chain chain) {
        chain.get('') { ctx -> ctx.render ctx.file('static/index.html') }
    }


    // 测试抛出错误
    def error(Chain chain) {
        chain.get('error') {ctx -> throw new RuntimeException('xxxxxxxxxxxx') }
    }


    @Lazy repo = bean(BaseRepo)

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
            ctx.render ok(ctx.sData)
        }
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


    // 获取上传的文件
    def file(Chain chain) {
        def fu = bean(FileUploader.class)
        chain.get('file/:fName') {ctx ->
            ctx.response.cookie('Cache-Control', "max-age=60")
            ctx.render fu.findFile(ctx.pathTokens.fName).toPath()
        }
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


    // js 文件
    def js(Chain chain) {
        chain.get("js/:fName") { ctx ->
            ctx.response.cookie('Cache-Control', "max-age=60")
            ctx.render ctx.file("static/js/$ctx.pathTokens.fName")
        }
    }


    // css 文件
    def css(Chain chain) {
        chain.get("css/:fName") {ctx ->
            ctx.response.cookie('Cache-Control', "max-age=60")
            ctx.render ctx.file("static/css/$ctx.pathTokens.fName")
        }
    }
}

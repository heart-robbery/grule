package ctrl

import core.module.jpa.BaseRepo
import dao.entity.Component
import ratpack.form.Form
import ratpack.handling.Chain
import service.FileUploader

import java.util.stream.Collectors

import static ctrl.common.ApiResp.ok


class MainCtrl extends CtrlTpl {

    @Lazy def repo = bean(BaseRepo)


    // 主页
    def index(Chain chain) {
        chain.get('') { ctx -> ctx.render ctx.file('static/index.html') }
    }


    def testHtml(Chain chain) {
        chain.get('test.html') { ctx -> ctx.render ctx.file('static/test.html') }
    }


    // 获取上传的文件
    def file(Chain chain) {
        def fu = bean(FileUploader.class)
        chain.get('file/:fName') {ctx ->
            ctx.response.cookie('Cache-Control', "max-age=120")
            ctx.render fu.findFile(ctx.pathTokens.fName).toPath()
        }
    }


    // html 文件
//    def html(Chain chain) {
//        chain.get(":fName.html") { ctx ->
//            // ctx.response.cookie('Cache-Control', "max-age=60")
//            ctx.render ctx.file("static/${ctx.pathTokens.fName}.html")
//        }
//    }


    // js 文件
    def js(Chain chain) {
        chain.get("js/:fName") { ctx ->
            // ctx.response.cookie('Cache-Control', "max-age=60")
            ctx.render ctx.file("static/js/$ctx.pathTokens.fName")
        }
        chain.get("js/lib/:fName") { ctx ->
            ctx.response.cookie('Cache-Control', "max-age=120")
            ctx.render ctx.file("static/js/lib/$ctx.pathTokens.fName")
        }
    }


    // vue 组件文件
    def coms(Chain chain) {
        chain.get("coms/:fName") { ctx ->
            // ctx.response.cookie('Cache-Control', "max-age=60")
            ctx.render ctx.file("static/coms/$ctx.pathTokens.fName")
        }
    }


    // css 文件
    def css(Chain chain) {
        chain.get("css/:fName") {ctx ->
            // ctx.response.cookie('Cache-Control', "max-age=60")
            ctx.render ctx.file("static/css/$ctx.pathTokens.fName")
        }
        chain.get("css/fonts/:fName") {ctx ->
            ctx.response.cookie('Cache-Control', "max-age=120")
            ctx.render ctx.file("static/css/fonts/$ctx.pathTokens.fName")
        }
        chain.get("css/lib/:fName") {ctx ->
            ctx.response.cookie('Cache-Control', "max-age=120")
            ctx.render ctx.file("static/css/lib/$ctx.pathTokens.fName")
        }
    }


    // 登录
    def login(Chain chain) {
        chain.post('login') {ctx ->
            ctx?['sData']?['uId'] = 'uid_1'
        }
    }


    // 搜索组件
    def search(Chain chain) {
        chain.get('search') {ctx ->
            def kw = ctx.request.queryParams.keyword
            if (kw) {
                def ret = ep.fire('cache.get', 'componentSearch', kw)
                if (ret == null) {
                    ret = repo.findPage(Component, 1, 10, {root, query, cb ->
                        cb.and(
                                cb.notEqual(root.get('enabled'), false),
                                cb.or(
                                        cb.like(root.get('tag1'), "%$kw%"), cb.like(root.get('tag2'), "%$kw%"),
                                        cb.like(root.get('tag3'), "%$kw%"), cb.like(root.get('tag4'), "%$kw%")
                                )
                        )
                    }).list.stream().flatMap{e ->
                        def list = []
                        if (e.tag1) list << [id: e.id, label: e.tag1]
                        if (e.tag2) list << [id: e.id, label: e.tag2]
                        if (e.tag3) list << [id: e.id, label: e.tag3]
                        if (e.tag4) list << [id: e.id, label: e.tag4]
                        list.stream()
                    }.collect(Collectors.toList())
                    ep.fire('cache.set', 'componentSearch', kw, ret)
                }
                ctx.render ok(ret)
            } else {
                def ret = ep.fire('cache.get', 'componentSearch', '')
                if (ret == null) {
                    ret = repo.findPage(Component, 1, 10, {root, query, cb ->
                        cb.desc(root.get('id'))
                        cb.notEqual(root.get('enabled'), false)
                    }).list.stream().flatMap{e ->
                        def list = []
                        if (e.tag1) list << [id: e.id, label: e.tag1]
                        if (e.tag2) list << [id: e.id, label: e.tag2]
                        if (e.tag3) list << [id: e.id, label: e.tag3]
                        if (e.tag4) list << [id: e.id, label: e.tag4]
                        list.stream()
                    }.collect(Collectors.toList())
                    ep.fire('cache.set', 'componentSearch', '', ret)
                }
                ctx.render ok(ret)
            }
        }
    }


    // 添加组件
    def addComponent(Chain chain) {
        chain.post('addComponent') {ctx ->
            // ctx.auth('admin')
            ctx.parse(Form).then{fd ->
                repo.saveOrUpdate(
                    new Component(
                        enabled: true,
                        tag1: fd.tag1, tag2: fd.tag2, tag3: fd.tag3, tag4: fd.tag4,
                        comment: fd.comment, htmlCode: fd.htmlCode,
                        javaCode: fd.javaCode, groovyCode: fd.groovyCode
                    )
                )
                ctx.render ok()
                ep.fire('cache.clear', 'componentSearch')
            }
        }
    }
}

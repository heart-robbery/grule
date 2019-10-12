package ctrl

import core.module.jpa.BaseRepo
import dao.entity.Component
import ratpack.form.Form
import ratpack.handling.Chain

import java.util.stream.Collectors

import static ctrl.common.ApiResp.ok


class MainCtrl extends CtrlTpl {

    @Lazy def repo = bean(BaseRepo)

    // 搜索组件
    def search(Chain chain) {
        chain.get('search') {ctx ->
            def kw = ctx.request.queryParams.keyword
            if (kw) {
                def ret = ep.fire('cache.get', 'componentSearch', kw)
                if (ret == null) {
                    ret = repo.findPage(Component, 0, 10, {root, query, cb ->
                        cb.and()
                        cb.or(
                            cb.like(root.get('tag1'), "%$kw%"), cb.like(root.get('tag2'), "%$kw%"),
                            cb.like(root.get('tag3'), "%$kw%"), cb.like(root.get('tag4'), "%$kw%")
                        )
                    }).list.stream().flatMap{e ->
                        def list = []
                        if (e.tag1) list << [id: e.id, label: e.tag1]
                        if (e.tag2) list << [id: e.id, label: e.tag2]
                        if (e.tag3) list << [id: e.id, label: e.tag3]
                        if (e.tag4) list << [id: e.id, label: e.tag4]
                        list
                    }.collect(Collectors.toList())
                    ep.fire('cache.set', 'componentSearch', kw, ret)
                }
                ctx.render ok(ret)
            } else {
                def ret = ep.fire('cache.get', 'componentSearchDefault', 'default')
                if (ret == null) {
                    ret = repo.findPage(Component, 0, 10, {root, query, cb ->
                        cb.desc(root.get('id'))
                    }).list.stream().flatMap{e ->
                        def list = []
                        if (e.tag1) list << [id: e.id, label: e.tag1]
                        if (e.tag2) list << [id: e.id, label: e.tag2]
                        if (e.tag3) list << [id: e.id, label: e.tag3]
                        if (e.tag4) list << [id: e.id, label: e.tag4]
                        list
                    }.collect(Collectors.toList())
                    ep.fire('cache.set', 'componentSearchDefault', 'default', ret)
                }
                ctx.render ok(ret)
            }
        }
    }


    // 添加组件
    def addComponent(Chain chain) {
        chain.post('addComponent') {ctx ->
            ctx.parse(Form).then{fd ->
                repo.saveOrUpdate(
                    new Component(
                        enabled: true,
                        tag1: fd.tag1, tag2: fd.tag2, tag3: fd.tag3,
                        comment: fd.comment, htmlCode: fd.htmlCode,
                        javaCode: fd.javaCode, groovyCode: fd.groovyCode
                    )
                )
            }
        }
    }
}

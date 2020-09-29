package ctrl

import core.Page
import core.ServerTpl
import core.Utils
import core.http.HttpContext
import core.http.mvc.ApiResp
import core.http.mvc.Ctrl
import core.http.mvc.Filter
import core.http.mvc.Path
import core.jpa.BaseRepo
import dao.entity.Permission
import dao.entity.User
import service.rule.UserSrv

@Ctrl(prefix = 'mnt/user')
class MntUserCtrl extends ServerTpl {
    @Lazy def repo = bean(BaseRepo, 'jpa_rule_repo')
    @Lazy def userSrv = bean(UserSrv)


    @Filter
    void filter(HttpContext ctx) {
        def name = ctx.getSessionAttr('name')
        if (!name) {
            ctx.response.status(401)
            ApiResp.fail('用户会话已失效, 请重新登录')
        }
    }


    @Path(path = 'page')
    ApiResp page(Integer page, Integer pageSize) {
        if (pageSize && pageSize > 50) return ApiResp.fail("pageSize max 50")
        ApiResp.ok(
            Page.of(
                repo.findPage(User, page, pageSize?:10) {root, query, cb ->
                    query.orderBy(cb.desc(root.get('createTime')))
                },
                {
                    Utils.toMapper(it)
                        .addConverter("permissions", {String name ->
                            def ps = repo.findList(Permission)
                            name.split(",").collect {n ->
                                for (def p : ps ) {
                                    if (p.enName == n) {
                                        return [(n): p.cnName]
                                    }
                                }
                            }
                        })
                        .build()
                }
            )
        )
    }


    @Path(path = 'add')
    ApiResp add(String name, String password, String[] ps) {
        ApiResp.ok(repo.saveOrUpdate(new User(name: name, password: password, permissions: ps?.findAll {it?.trim()}?.join(','))))
    }


    @Path(path = 'del/:id')
    ApiResp del(Long id) {
        ApiResp.ok(repo.delete(User, id))
    }


    @Path(path = 'update')
    ApiResp update(HttpContext ctx, Long id, String name, String[] ps) {
        def user = repo.findById(User, id)
        if (!user) return ApiResp.fail("用户不存在")
        if (name && user.name != name) {
            if (repo.find(User) {root, query, cb -> cb.equal(root.get('name'), name)}) {
                return ApiResp.fail("$name already exist")
            }
            user.name = name
        }
        ctx.auth('grant')
        user.permissions = ps?.findAll {it?.trim()}.join(',')
        if (ctx.getSessionAttr('id') == id) { //如果是当前用户
            ctx.setSessionAttr('permissions', ps as Set)
        }
        ApiResp.ok(repo.saveOrUpdate(user))
    }


    @Path(path = 'restPassword')
    ApiResp restPassword(HttpContext ctx, Long id, String newPassword) {
        ctx.auth('password-reset')
        def user = repo.findById(User, id)
        if (!user) return ApiResp.fail("用户不存在")
        user.password = newPassword
        repo.saveOrUpdate(user)
        ApiResp.ok()
    }


    @Path(path = 'permissions')
    ApiResp permissions() {
        ApiResp.ok(repo.findList(Permission))
    }
}

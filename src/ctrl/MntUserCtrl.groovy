package ctrl

import cn.xnatural.http.ApiResp
import cn.xnatural.http.Ctrl
import cn.xnatural.http.HttpContext
import cn.xnatural.http.Path
import cn.xnatural.jpa.Page
import cn.xnatural.jpa.Repo
import core.ServerTpl
import core.Utils
import dao.entity.Permission
import dao.entity.User
import service.rule.UserSrv

@Ctrl(prefix = 'mnt/user')
class MntUserCtrl extends ServerTpl {
    @Lazy def repo = bean(Repo, 'jpa_rule_repo')
    @Lazy def userSrv = bean(UserSrv)


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
                            def ps = repo.findList(Permission, null)
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
        def u = repo.saveOrUpdate(new User(name: name, password: password, permissions: ps?.findAll {it?.trim()}?.join(',')))
        ApiResp.ok(u.id)
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
        repo.saveOrUpdate(user)
        ApiResp.ok().attr('id', user.id).attr('name', user.name)
            .attr('permissions', user.permissions?.split(","))
    }


    @Path(path = 'changePwd')
    ApiResp changePwd(HttpContext ctx, Long id, String newPassword, String oldPassword) {
        if (ctx.getSessionAttr("id", Long) != id) {
            return ApiResp.fail('只能改当前用户的密码')
        }
        def user = repo.findById(User, id)
        if (!user) return ApiResp.fail("用户不存在")
        if (oldPassword != user.password) return ApiResp.fail('密码错误')
        user.password = newPassword
        repo.saveOrUpdate(user)
        ApiResp.ok()
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
        ApiResp.ok(repo.findList(Permission, null))
    }


    @Path(path = 'delPermission/:id', method = 'post')
    ApiResp delPermission(HttpContext ctx, Long id) {
        ctx.auth("grant")
        ApiResp.ok(repo.delete(Permission, id))
    }


    @Path(path = 'updatePermission', method = 'post')
    ApiResp updatePermission(HttpContext ctx, Long id, String enName, String cnName, String comment) {
        ctx.auth("grant")
        def p = repo.findById(Permission, id)
        if (!p) return ApiResp.fail('未找到权限: ' + id)
        if (p.enName != enName && repo.find(Permission) {root, query, cb -> cb.equal(root.get('enName'), enName)}) {
            return ApiResp.fail("权限名'$enName'已存在")
        }
        if (p.cnName != cnName && repo.find(Permission) {root, query, cb -> cb.equal(root.get('cnName'), cnName)}) {
            return ApiResp.fail("权限名'$cnName'已存在")
        }
        p.enName = enName
        p.cnName = cnName
        p.comment = comment
        ApiResp.ok(repo.saveOrUpdate(p))
    }


    @Path(path = 'addPermission', method = 'post')
    ApiResp addPermission(HttpContext ctx, String enName, String cnName, String comment) {
        ctx.auth("grant")
        def p = new Permission(enName: enName, cnName: cnName, comment: comment)
        if (repo.find(Permission) {root, query, cb -> cb.equal(root.get('enName'), enName)}) {
            return ApiResp.fail("权限名'$enName'已存在")
        }
        if (repo.find(Permission) {root, query, cb -> cb.equal(root.get('cnName'), cnName)}) {
            return ApiResp.fail("权限名'$cnName'已存在")
        }
        repo.saveOrUpdate(p)
        ApiResp.ok(p)
    }


    @Path(path = 'permissionPage')
    ApiResp permissionPage(Integer page, Integer pageSize, String kw) {
        if (pageSize && pageSize > 20) return ApiResp.fail("pageSize max 20")
        ApiResp.ok(
            repo.findPage(Permission, page, pageSize?:10) {root, query, cb ->
                query.orderBy(cb.desc(root.get('updateTime')))
                if (kw) {
                    return cb.or(cb.like(root.get('enName'), '%' + kw + '%'), cb.like(root.get('cnName'), '%' + kw + '%'))
                }
            }
        )
    }
}

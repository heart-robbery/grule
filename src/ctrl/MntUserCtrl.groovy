package ctrl

import cn.xnatural.app.ServerTpl
import cn.xnatural.app.Utils
import cn.xnatural.http.ApiResp
import cn.xnatural.http.Ctrl
import cn.xnatural.http.HttpContext
import cn.xnatural.http.Path
import cn.xnatural.jpa.Page
import cn.xnatural.jpa.Repo
import entity.Decision
import entity.Permission
import entity.User
import service.rule.UserSrv

import javax.persistence.criteria.Predicate

@Ctrl(prefix = 'mnt/user')
class MntUserCtrl extends ServerTpl {
    @Lazy def repo = bean(Repo, 'jpa_rule_repo')
    @Lazy def userSrv = bean(UserSrv)


    @Path(path = 'page')
    ApiResp page(HttpContext hCtx, Integer page, Integer pageSize, String kw) {
        if (pageSize && pageSize > 50) return ApiResp.fail("Param pageSize <=50")
        ApiResp.ok(
            Page.of(
                repo.findPage(User, page, pageSize?:8) {root, query, cb ->
                    query.orderBy(cb.desc(root.get('createTime')))
                    def ps = []
                    if (!hCtx.hasAuth("grant")) {
                        ps << cb.equal(root.get("name"), hCtx.getSessionAttr("uName")) // 当前用户只能看自己的信息
                    }
                    if (kw) {
                        ps << cb.like(root.get("name"), "%" + kw + "%")
                    }
                    cb.and(ps.toArray(new Predicate[ps.size()]))
                },
                {
                    Utils.toMapper(it).ignore("metaClass", "password")
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
    ApiResp add(HttpContext hCtx, String name, String password, String[] permissionIds) {
        hCtx.auth("grant")
        if (!name) return ApiResp.fail("Param name not empty")
        if (!password) return ApiResp.fail("Param password not empty")
        def u = repo.saveOrUpdate(new User(name: name, password: password, permissions: permissionIds?.findAll {it?.trim()}?.join(',')))
        ApiResp.ok(u.id)
    }


    @Path(path = 'del/:id')
    ApiResp del(HttpContext hCtx, Long id) {
        hCtx.auth("grant")
        ApiResp.ok(repo.delete(User, id))
    }


    @Path(path = 'update')
    ApiResp update(HttpContext hCtx, Long id, String name, String[] permissionIds) {
        if (!id) return ApiResp.fail("Param id required")
        def user = repo.findById(User, id)
        if (!user) return ApiResp.fail("用户不存在")
        if (name && user.name != name) {
            if (repo.find(User) {root, query, cb -> cb.equal(root.get('name'), name)}) {
                return ApiResp.fail("$name already exist")
            }
            user.name = name
        }
        hCtx.auth('grant')
        user.permissions = permissionIds?.findAll {it?.trim()}?.join(',')
        if (hCtx.getSessionAttr('id') == id) { //如果是当前用户
            hCtx.setSessionAttr('permissions', permissionIds)
        }
        repo.saveOrUpdate(user)
        ApiResp.ok().attr('id', user.id).attr('name', user.name)
            .attr('permissions', user.permissions?.split(","))
    }


    @Path(path = 'changePwd')
    ApiResp changePwd(HttpContext hCtx, Long id, String newPassword, String oldPassword) {
        if (!id) return ApiResp.fail("Param id required")
        if (!newPassword) return ApiResp.fail("Param newPassword required")
        if (Utils.to(hCtx.getSessionAttr("id"), Long) != id) {
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
    ApiResp restPassword(HttpContext hCtx, Long id, String newPassword) {
        if (!id) return ApiResp.fail("Param id required")
        if (!newPassword) return ApiResp.fail("Param newPassword required")
        hCtx.auth('password-reset')
        def user = repo.findById(User, id)
        if (!user) return ApiResp.fail("用户不存在")
        user.password = newPassword
        repo.saveOrUpdate(user)
        ApiResp.ok()
    }


//    @Path(path = 'permissions')
//    ApiResp permissions() {
//        ApiResp.ok(repo.findList(Permission, null))
//    }


    @Path(path = 'delPermission/:id', method = 'post')
    ApiResp delPermission(HttpContext hCtx, Long id) {
        hCtx.auth("grant")
        ApiResp.ok(repo.delete(Permission, id))
    }


    @Path(path = 'updatePermission', method = 'post')
    ApiResp updatePermission(HttpContext hCtx, Long id, String enName, String cnName, String comment) {
        if (!id) return ApiResp.fail("Param id required")
        hCtx.auth("grant")
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
    ApiResp addPermission(HttpContext hCtx, String enName, String cnName, String comment) {
        if (!enName) return ApiResp.fail("Param enName required")
        if (!cnName) return ApiResp.fail("Param cnName required")
        hCtx.auth("grant")
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
    ApiResp permissionPage(HttpContext hCtx, Integer page, Integer pageSize, String kw) {
        if (page != null && page < 1) return ApiResp.fail("Param page >=1")
        if (pageSize != null && pageSize > 20) return ApiResp.fail("Param pageSize <=20 and >=1")
        List ids
        if (!hCtx.hasAuth("grant")) {
            ids = repo.findList(Decision) {root, query, cb ->
                cb.equal(root.get("creator"), hCtx.getSessionAttr("uName"))
            }.findResults {it.id}
        }
        ApiResp.ok(
            repo.findPage(Permission, page?:1, pageSize?:10) {root, query, cb ->
                query.orderBy(cb.desc(root.get('updateTime')))
                def ps = []
                if (kw) {
                    ps << cb.or(cb.like(root.get("enName"), "%" + kw + "%"), cb.like(root.get("cnName"), "%" + kw + "%"))
                }
                if (ids) {
                    ps << root.get("mark").in(ids)
                }
                cb.and(ps.toArray(new Predicate[ps.size()]))
            }
        )
    }


    /**
     * 是否有某个权限
     * @param hCtx
     * @param permission 权限标识
     * @return
     */
    @Path(path = 'hasPermission')
    ApiResp hasPermission(HttpContext hCtx, String permission) {
        if (!permission) return ApiResp.fail("Param permission not empty")
        ApiResp.ok(hCtx.hasAuth(permission))
    }


    /**
     * 获取某个用户的所有权限
     * @param hCtx
     * @param uId
     * @return
     */
    @Path(path = 'getUserPermissions/:uId')
    ApiResp getUserPermissions(HttpContext hCtx, Long uId) {
        if (!uId) return ApiResp.fail("Param uId not empty")
        if (!Utils.to(hCtx.getSessionAttr("uId"), Long) == uId) { //非当前用户验证权限
            hCtx.auth("grant")
        }
        def pIds = repo.findById(User, uId).permissions?.split(",")
        if (pIds) {
            return ApiResp.ok(
                repo.findList(Permission) {root, query, cb -> root.get("enName").in(pIds)}
            )
        }
        ApiResp.ok()
    }
}

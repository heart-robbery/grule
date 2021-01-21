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
                    query.orderBy(cb.desc(root.get('updateTime')))
                    def ps = []
                    if (!hCtx.hasAuth("grant") && !hCtx.hasAuth("grant-user")) {
                        ps << cb.equal(root.get("id"), hCtx.getSessionAttr("uId")) // 当前用户只能看自己的信息
                    } else if (!hCtx.hasAuth("grant") && hCtx.hasAuth("grant-user")) {
                        ps << cb.equal(root.get("group"), hCtx.getSessionAttr("uGroup")) // 能看到同一组的所有用户
                    }
                    if (kw) {
                        ps << cb.like(root.get("name"), "%" + kw + "%")
                    }
                    cb.and(ps.toArray(new Predicate[ps.size()]))
                },
                {
                    Utils.toMapper(it).ignore("metaClass", "password")
                        .addConverter("permissions", {String pIds ->
                            def ps = repo.findList(Permission, null)
                            pIds.split(",").collect {pId ->
                                if (!pId) return null
                                for (def p : ps ) {
                                    if (p.enName == pId) {
                                        return [(pId): p.cnName]
                                    }
                                }
                            }.findAll {it}
                        })
                        .build()
                }
            )
        )
    }


    @Path(path = 'add')
    ApiResp add(HttpContext hCtx, String name, String password, String group, String[] permissionIds) {
        hCtx.auth("grant", "grant-user")
        if (!hCtx.hasAuth("grant") && hCtx.hasAuth("grant-user")) {
            if (permissionIds && permissionIds.contains("grant-user")) {
                return ApiResp.fail("Not permission")
            }
            if (group) return ApiResp.fail("Param group unnecessary")
            group = hCtx.getSessionAttr("uGroup") // grant-user权限 创建的用户 自动同组
        }
        if (!name) return ApiResp.fail("Param name not empty")
        if (!password) return ApiResp.fail("Param password not empty")
        def u = repo.saveOrUpdate(new User(name: name, password: password, group: group, permissions: permissionIds?.findAll {it?.trim()}?.join(',')))
        ApiResp.ok(u.id)
    }


    @Path(path = 'del/:id')
    ApiResp del(HttpContext hCtx, Long id) {
        if (!id) return ApiResp.fail("Param id required")
        if (hCtx.hasAuth("grant")) {
            return ApiResp.ok(repo.delete(User, id))
        } else if (hCtx.hasAuth("grant-user")) {
            def eUser = repo.findById(User, id)
            if (eUser.group != hCtx.getSessionAttr("uGroup")) {
                return ApiResp.fail("Not permission")
            } else {
                repo.delete(eUser)
                return ApiResp.ok(true)
            }
        }
        hCtx.response.status(403)
        return ApiResp.fail("Not permission")
    }


    @Path(path = 'update')
    ApiResp update(HttpContext hCtx, Long id, String name, String group, String[] permissionIds) {
        hCtx.auth("grant", "grant-user")
        if (!id) return ApiResp.fail("Param id required")
        def user = repo.findById(User, id)
        if (!user) return ApiResp.fail("用户不存在")
        String originGroup //原来的group
        if (!hCtx.hasAuth("grant") && hCtx.hasAuth("grant-user")) {
            if (user.group != hCtx.getSessionAttr("uGroup")) {
                return ApiResp.fail("Not permission")
            }
            if (permissionIds && permissionIds.contains("grant-user")) {
                return ApiResp.fail("Not permission")
            }
            if (group) { // grant-user 并且非 grant, 则不能修改用户组名
                return ApiResp.fail("Param group unnecessary")
            }
        } else {
            // 修改用户所属组
            if (group && user.group != group) {
                if (user.permissions?.contains("user-grant")) {
                    originGroup = user.group
                }
                user.group = group
            }
        }
        if (name && user.name != name) { // 名字修改
            if (repo.find(User) {root, query, cb -> cb.equal(root.get('name'), name)}) {
                return ApiResp.fail("$name already exist")
            }
            user.name = name
        }
        user.permissions = permissionIds?.findAll {it?.trim()}?.join(',')
        if (hCtx.getSessionAttr('id') == id) { //如果是当前用户
            hCtx.setSessionAttr('permissions', permissionIds)
        }
        if (originGroup) { //如果修改了 组管理员(即有grant-user权限)用户 的组名, 则需要把同一组的其它用户的group同步修改
            repo.trans {se ->
                repo.saveOrUpdate(user)
                se.createQuery("update User set group=:newGroup where group=:oldGroup")
                    .setParameter("newGroup", group).setParameter("oldGroup", originGroup)
                    .executeUpdate()
            }
        } else {
            repo.saveOrUpdate(user)
        }
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
        hCtx.auth("grant", "grant-user")
        if (!id) return ApiResp.fail("Param id required")
        if (!newPassword) return ApiResp.fail("Param newPassword required")
        //hCtx.auth('password-reset')
        def user = repo.findById(User, id)
        if (!user) return ApiResp.fail("用户不存在")
        if (!hCtx.hasAuth("grant") && hCtx.hasAuth("grant-user")) {
            if (user.group != hCtx.getSessionAttr("uGroup")) {
                return ApiResp.fail("Not permission")
            }
        }
        user.password = newPassword
        repo.saveOrUpdate(user)
        ApiResp.ok()
    }


    @Path(path = 'delPermission/:id', method = 'post')
    ApiResp delPermission(HttpContext hCtx, Long id) {
        hCtx.auth("grant")
        if (!id) return ApiResp.fail("Param id required")
        def permission = repo.findById(Permission, id)
        if (permission.mark) return ApiResp.fail("Not allow delete dynamic permission")
        repo.delete(permission)
        ApiResp.ok(permission)
    }


    @Path(path = 'updatePermission', method = 'post')
    ApiResp updatePermission(HttpContext hCtx, Long id, String enName, String cnName, String comment) {
        hCtx.auth("grant")
        if (!id) return ApiResp.fail("Param id required")
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
        hCtx.auth("grant")
        if (!enName) return ApiResp.fail("Param enName required")
        if (!cnName) return ApiResp.fail("Param cnName required")
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
    ApiResp permissionPage(HttpContext hCtx, Integer page, Integer pageSize, String kw, String[] notPermissionIds) {
        // hCtx.auth("grant", "grant-user")
        if (page != null && page < 1) return ApiResp.fail("Param page >=1")
        if (pageSize != null && pageSize > 20) return ApiResp.fail("Param pageSize <=20 and >=1")
        List pIds // 能查看的权限id
        if (!hCtx.hasAuth("grant") && !hCtx.hasAuth("grant-user")) { //普通用户只能看到 自己创建的动态权限
            pIds = repo.findList(Decision) {root, query, cb ->
                cb.equal(root.get("creator"), hCtx.getSessionAttr("uName"))
            }.findResults {it.id}
            if (!pIds) return ApiResp.ok()
        } else if (hCtx.hasAuth("grant-user")) { // grant-user 只能看到同一组所有用户创建的动态权限, 和公用静态权限
            pIds = repo.findList(Decision) {root, query, cb ->
                root.get("creator").in(repo.findList(User) {root1, query1, cb1 -> cb1.equal(root1.get("group"), hCtx.getSessionAttr("uGroup"))})
            }.findResults {it.id}
            pIds.addAll(bean(UserSrv).staticPermission)
            pIds.remove("grant-user") //去掉 grant-user
        }
        ApiResp.ok(
            repo.findPage(Permission, page?:1, pageSize?:10) {root, query, cb ->
                query.orderBy(cb.desc(root.get('updateTime')))
                def ps = []
                if (notPermissionIds) {
                    ps << root.get("enName").in(notPermissionIds).not()
                }
                if (kw) {
                    ps << cb.or(cb.like(root.get("enName"), "%" + kw + "%"), cb.like(root.get("cnName"), "%" + kw + "%"))
                }
                if (pIds) {
                    ps << root.get("mark").in(pIds)
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
            hCtx.auth("grant", "grant-user")
        }
        def eUser = repo.findById(User, uId)
        if (!eUser) return ApiResp.fail("用户不存在")
        if (!hCtx.hasAuth("grant") && hCtx.hasAuth("grant-user")) {
            if (eUser.group != hCtx.getSessionAttr("uGroup")) {
                return ApiResp.fail("Not permission")
            }
        }
        def pIds = eUser.permissions?.split(",")
        if (pIds) {
            return ApiResp.ok(
                repo.findList(Permission) {root, query, cb -> root.get("enName").in(pIds)}
            )
        }
        ApiResp.ok()
    }
}

package ctrl

import cn.xnatural.app.ServerTpl
import cn.xnatural.app.Utils
import cn.xnatural.http.ApiResp
import cn.xnatural.http.Ctrl
import cn.xnatural.http.HttpContext
import cn.xnatural.http.Path
import cn.xnatural.jpa.Page
import cn.xnatural.jpa.Repo
import entity.Permission
import entity.User

import javax.persistence.criteria.Predicate

/**
 * 用户, 权限相关接口
 */
@Ctrl(prefix = 'mnt/user')
class MntUserCtrl extends ServerTpl {
    @Lazy def repo = bean(Repo, 'jpa_rule_repo')


    /**
     * 用户分页查询接口
     * @param hCtx
     * @param page >= 1
     * @param pageSize <= 50
     * @param kw 搜索关键字
     * @return
     */
    @Path(path = 'page')
    ApiResp<Map> page(HttpContext hCtx, Integer page, Integer pageSize, String kw) {
        if (pageSize && pageSize > 50) return ApiResp.fail("Param pageSize <=50")
        boolean hasGrant = hCtx.hasAuth("grant")
        boolean hasGrantUser = hCtx.hasAuth("grant-user")
        boolean hasUserDel = hCtx.hasAuth("user-del")
        ApiResp.ok(
                repo.findPage(User, page, pageSize?:8) {root, query, cb ->
                    query.orderBy(cb.asc(root.get('createTime')))
                    def ps = []
                    //1. 非grant,grant-user用户: 只能看自己的信息
                    if (!hasGrant && !hasGrantUser) {
                        ps << cb.equal(root.get("id"), hCtx.getSessionAttr("uId"))
                    }
                    //2. 只grant-user用户: 能看到同一组的所有用户
                    else if (!hasGrant && hasGrantUser) {
                        if (hCtx.getSessionAttr("uGroup")) {
                            ps << cb.equal(root.get("group"), hCtx.getSessionAttr("uGroup"))
                        } else {
                            ps << cb.equal(root.get("id"), hCtx.getSessionAttr("uId"))
                        }
                    }
                    //3. grant用户: 默认可以看所有用户
                    else {}

                    if (kw) {
                        ps << cb.like(root.get("name"), "%" + kw + "%")
                    }
                    cb.and(ps.toArray(new Predicate[ps.size()]))
                }.to {user ->
                    Utils.toMapper(user).ignore("metaClass", "password")
                            .addConverter("permissions", {String pIdStr ->
                                def pIds = pIdStr?.split(",")
                                if (!pIds) return []
                                def ps = repo.findList(Permission) {root, query, cb -> root.get("enName").in(pIds)}
                                pIds.collect {pId ->
                                    if (!pId) return null
                                    for (def p : ps ) {
                                        if (p.enName == pId) {
                                            return [(pId): p.cnName]
                                        }
                                    }
                                }.findAll {it}
                            })
                            .add("_readonly", {
                                if (hasGrant) return false
                                if (hasGrantUser) {
                                    if (user.permissions?.split(",")?.contains("grant")) return true
                                    else if (user.permissions?.contains("grant-user")) {
                                        if (Utils.to(hCtx.getSessionAttr("uId"), Long) == user.id) return false
                                        else return true
                                    } else return false
                                }
                                return true
                            }())
                            .add("_deletable", {
                                if (!hasUserDel) return false
                                if (hasGrant) return true
                                if (hasGrantUser) {
                                    if (user.permissions?.split(",")?.contains("grant")) return false
                                    else if (user.permissions?.contains("grant-user")) return false
                                    else return true
                                }
                                return false
                            }())
                            .add("_restPassword", {
                                if (hasGrant) return true
                                if (hasGrantUser) {
                                    if (user.permissions?.contains("grant")) return false
                                    else return true
                                }
                                return false
                            }())
                            .build()
                }
        )
    }


    /**
     * 添加用户
     * @param hCtx
     * @param name 用户名
     * @param password 密码
     * @param group 组名
     * @param permissionIds 权限标识
     * @return
     */
    @Path(path = 'add')
    ApiResp add(HttpContext hCtx, String name, String password, String group, String[] permissionIds) {
        //1. grant, grant-user 可以添加用户
        hCtx.auth("grant", "grant-user", "user-add")
        if (!name) return ApiResp.fail("Param name not empty")
        name = name.trim()
        if (name.length() < 2) return ApiResp.fail("Param name length >= 2")
        if (!password) return ApiResp.fail("Param password not empty")
        password = password.trim()
        if (password.length() < 2) return ApiResp.fail("Param password length >= 2")
        ApiResp resp = ApiResp.ok()

        //2. 只grant-user: 不能分配grant, grant-user; 添加的用户默认同一组, 忽略group参数
        if (!hCtx.hasAuth("grant") && hCtx.hasAuth("grant-user")) {
            if (permissionIds?.contains("grant") || permissionIds?.contains("grant-user")) {
                return ApiResp.fail("Not permission")
            }
            // 验证是否都是可以分配的权限
            for (def pId: permissionIds) {
                if (!(permissionPage(hCtx, 1, 1, null, pId, null).data?.list?.find {it.enName == pId})) {
                    return ApiResp.fail("Not permission, $pId")
                }
            }

            if (group) resp.desc("Param group unnecessary, ignore")
            group = hCtx.getSessionAttr("uGroup") // grant-user权限 创建的用户 自动同组
        }

        def eUser = repo.saveOrUpdate(
                new User(name: name, password: password, group: group, permissions: permissionIds?.findAll {it?.trim()}?.unique()?.join(','))
        )
        resp.setData(eUser.id)
        return resp
    }


    /**
     * 删除用户
     * @param hCtx
     * @param id id
     * @return
     */
    @Path(path = 'del/:id')
    ApiResp del(HttpContext hCtx, Long id) {
        //1. grant, grant-user 可以删除用户
        hCtx.auth("grant", "grant-user", "user-del")
        if (!id) return ApiResp.fail("Param id required")

        //2. 只grant-user: 只能删除同组用户(不包括自己,不包括grant-user,grant)
        if (!hCtx.hasAuth("grant") && hCtx.hasAuth("grant-user")) {
            // 不能删除自己
            if (id == Utils.to(hCtx.getSessionAttr("uId"), Long)) {
                ApiResp.fail("Not permission, delete self")
            }
            // 没有组,不能删除任何用户
            if (!hCtx.getSessionAttr("uGroup")) {
                ApiResp.fail("Not permission, no deletable user")
            }
            def eUser = repo.findById(User, id)
            // 不能删除非同组的用户
            if (eUser.group != hCtx.getSessionAttr("uGroup")) {
                return ApiResp.fail("Not permission, not same group")
            }
            // 不能删除grant,grant-user用户
            if (eUser.permissions?.contains("grant")) {
                return ApiResp.fail("Not permission, has grant")
            }
        }
        //3. grant用户: 随便删
        return ApiResp.ok(repo.delete(User, id))
    }


    /**
     * 更新用户信息
     * @param hCtx
     * @param id id
     * @param group 组
     * @param permissionIds 权限标识
     * @return
     */
    @Path(path = 'update')
    ApiResp update(HttpContext hCtx, Long id, String group, String[] permissionIds) {
        //1. grant, grant-user 可以更新用户
        hCtx.auth("grant", "grant-user")
        if (!id) return ApiResp.fail("Param id required")
        def user = repo.findById(User, id)
        if (!user) return ApiResp.fail("用户不存在")
        ApiResp resp = ApiResp.ok()
        //更新用户是否是自己
        boolean targetUserIsMe = id == Utils.to(hCtx.getSessionAttr("uId"), Long)

        //2. 只grant-user: 只能分配同组用户(包括自己,非grant,grant-user)的权限(自己拥有的(除grant,grant-user),同组用户产生的动态权限); 忽略group参数
        String originGroup //原来的group
        if (!hCtx.hasAuth("grant") && hCtx.hasAuth("grant-user")) {
            // 没有组, 则只能为自己分配权限
            if (!hCtx.getSessionAttr("uGroup") && !targetUserIsMe) {
                return ApiResp.fail("Not permission, only allow self")
            }
            // 不能为非同组分配权限
            if (user.group != hCtx.getSessionAttr("uGroup")) {
                return ApiResp.fail("Not permission, not same group")
            }
            def targetUserPIds = user.permissions?.split(",")
            // 不能为grant-user用户(除自己)分配权限
            if (targetUserPIds?.contains("grant-user") && !targetUserIsMe) {
                return ApiResp.fail("Not permission, has grant-user")
            }
            // 不能为grant的用户分配权限
            if (targetUserPIds?.contains("grant")) {
                return ApiResp.fail("Not permission, has grant")
            }

            // 不能分配grant-user权限(除自己)
            if (permissionIds?.contains("grant-user") && !targetUserIsMe) {
                return ApiResp.fail("Not permission, other grant-user")
            }
            // 不能分配grant权限
            if (permissionIds?.contains("grant")) {
                return ApiResp.fail("Not permission, grant")
            }
            // 验证是否都是可以分配的权限
            for (def pId : permissionIds) {
                // 自己已有的权限不必验证
                if (targetUserPIds?.contains(pId)) continue

                if (!(permissionPage(hCtx, 1, 1, null, pId, null).data?.list?.find {it.enName == pId})) {
                    return ApiResp.fail("Not permission, " + pId)
                }
            }

            // 忽略group参数
            if (group && group != hCtx.getSessionAttr("uGroup")) {
                resp.desc("Param group unnecessary, ignore")
            }
        }

        //3. grant用户: 如果修改了 组管理员(即有grant-user权限)用户 的组名, 则需要把同一组的其它用户的group同步修改
        if (hCtx.hasAuth("grant")) {
            if (group && user.group != group) { // 修改用户所属组
                if (user.permissions?.contains("grant-user")) {
                    originGroup = user.group
                }
                user.group = group
            }
        }
        user.permissions = permissionIds?.findAll {it?.trim()}?.join(',')

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
        return resp.attr('id', user.id).attr('name', user.name)
                .attr('permissions', user.permissions?.split(","))
    }


    @Path(path = 'changePwd')
    ApiResp changePwd(HttpContext hCtx, Long id, String newPassword, String oldPassword) {
        if (!id) return ApiResp.fail("Param id required")
        if (!newPassword) return ApiResp.fail("Param newPassword required")
        if (Utils.to(hCtx.getSessionAttr("id"), Long) != id) {
            return ApiResp.fail("Not permission, only allow change self password")
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
        //1. grant, grant-user 可以重置密码
        hCtx.auth("grant", "grant-user")
        if (!id) return ApiResp.fail("Param id required")
        if (!newPassword) return ApiResp.fail("Param newPassword required")
        def user = repo.findById(User, id)
        if (!user) return ApiResp.fail("用户不存在")

        //2. 只grant-user: 只能重置同组(非自己,非grant,grant-user)的密码
        if (!hCtx.hasAuth("grant") && hCtx.hasAuth("grant-user")) {
            if (user.group != hCtx.getSessionAttr("uGroup")) {
                return ApiResp.fail("Not permission, not same group")
            }
            if (user.permissions?.contains("grant")) {
                return ApiResp.fail("Not permission, grant or grant-user")
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
    ApiResp<Page<Permission>> permissionPage(HttpContext hCtx, Integer page, Integer pageSize, String kw, String permissionId, String[] notPermissionIds) {
        if (page != null && page < 1) return ApiResp.fail("Param page >=1")
        if (pageSize != null && pageSize > 20) return ApiResp.fail("Param pageSize <=20 and >=1")
        //1. grant: 查看所有
        if (hCtx.hasAuth("grant")) {
            return ApiResp.ok(
                repo.findPage(Permission, page?:1, pageSize?:10) {root, query, cb ->
                    query.orderBy(cb.desc(root.get('updateTime')))
                    def ps = []
                    if (permissionId) {
                        ps << cb.equal(root.get("enName"), permissionId)
                    }
                    if (notPermissionIds) {
                        ps << root.get("enName").in(notPermissionIds).not()
                    }
                    if (kw) {
                        ps << cb.or(cb.like(root.get("enName"), "%" + kw + "%"), cb.like(root.get("cnName"), "%" + kw + "%"))
                    }
                    cb.and(ps.toArray(new Predicate[ps.size()]))
                }
            )
        }
        //2. grant-user: 只能看到同一组所有用户创建的动态权限, 和自己的权限(除grant,grant-user)
        else if (hCtx.hasAuth("grant-user")) { // 组管理员
            List uNames = [hCtx.getSessionAttr("uName")]
            if (hCtx.getSessionAttr("uGroup")) { // 组名存在, 则把组内所有用户名找出来
                uNames = repo.trans{se -> se.createQuery("select name from User where group=:gp").setParameterList("gp", hCtx.getSessionAttr("uGroup")).list()}
            }
            def marks = repo.trans{se -> se.createQuery("select id from Decision where creator in (:creator)").setParameterList("creator", uNames).list()}
            def myPIds = hCtx.getAttr("permissions", Set)?.findAll {String pId -> pId != "grant" && pId != "grant-user" && !pId.startsWith("user-")} // 当前用户的所有权限
            if (!marks && !myPIds) return ApiResp.ok(Page.empty())
            return ApiResp.ok(
                repo.findPage(Permission, page?:1, pageSize?:10) {root, query, cb ->
                    query.orderBy(cb.desc(root.get('updateTime')))
                    def ps = []
                    if (marks && myPIds) {
                        ps << cb.or(root.get("mark").in(marks), root.get("enName").in(myPIds))
                    } else if (marks) {
                        ps << root.get("mark").in(marks)
                    } else if (myPIds) {
                        ps << root.get("enName").in(myPIds)
                    }
                    if (permissionId) {
                        ps << cb.equal(root.get("enName"), permissionId)
                    }
                    if (notPermissionIds) {
                        ps << root.get("enName").in(notPermissionIds).not()
                    }
                    if (kw) {
                        ps << cb.or(cb.like(root.get("enName"), "%" + kw + "%"), cb.like(root.get("cnName"), "%" + kw + "%"))
                    }
                    cb.and(ps.toArray(new Predicate[ps.size()]))
                }
            )
        }
        //3. 其他: 普通用户只能看到自己创建的动态权限
        else {
            def marks = repo.trans{se -> se.createQuery("select id from Decision where creator=:creator").setParameter("creator", hCtx.getSessionAttr("uName")).list()}
            if (!marks) return ApiResp.ok(Page.empty())
            return ApiResp.ok(
                repo.findPage(Permission, page?:1, pageSize?:10) {root, query, cb ->
                    query.orderBy(cb.desc(root.get('updateTime')))
                    def ps = []
                    ps << root.get("mark").in(marks)
                    if (permissionId) {
                        ps << cb.equal(root.get("enName"), permissionId)
                    }
                    if (notPermissionIds) {
                        ps << root.get("enName").in(notPermissionIds).not()
                    }
                    if (kw) {
                        ps << cb.or(cb.like(root.get("enName"), "%" + kw + "%"), cb.like(root.get("cnName"), "%" + kw + "%"))
                    }
                    cb.and(ps.toArray(new Predicate[ps.size()]))
                }
            )
        }
    }


    /**
     * 组 分页查询
     * @return
     */
    @Path(path = 'groupPage')
    ApiResp<Page<String>> groupPage(HttpContext hCxt, Integer page, Integer pageSize, String kw) {
        if (page != null && page < 1) return ApiResp.fail("Param page >=1")
        if (pageSize != null && pageSize > 20) return ApiResp.fail("Param pageSize <=20 and >=1")
        ApiResp.ok(
                repo.sqlPage("select gp from ${repo.tbName(User)} ${kw ? 'where gp like ?' : ''} group by gp", page?:1, pageSize?:10, (kw ? ("%" + kw + "%"): null))
                        .to {it.get("gp")?:it.get("GP")}
        )
    }
}
package service.rule

import cn.xnatural.enet.event.EL
import cn.xnatural.jpa.Repo
import core.ServerTpl
import dao.entity.Permission
import dao.entity.User

class UserSrv extends ServerTpl {

    @Lazy def repo = bean(Repo, 'jpa_rule_repo')


    @EL(name = "jpa_rule.started", async = true)
    void init() {
        initUserPermission()
    }


    /**
     * 初始化 权限数据
     */
    protected void initUserPermission() {
        if (repo.count(Permission) < 1) {
            log.info("初始化默认权限")
            repo.saveOrUpdate(new Permission(enName: 'grant', cnName: '权限分配'))
            repo.saveOrUpdate(new Permission(enName: 'user-add', cnName: '新增用户'))
            repo.saveOrUpdate(new Permission(enName: 'user-del', cnName: '删除用户'))
            repo.saveOrUpdate(new Permission(enName: 'password-reset', cnName: '密码重置'))
            repo.saveOrUpdate(new Permission(enName: 'decision-del', cnName: '删除决策'))
            repo.saveOrUpdate(new Permission(enName: 'decision-add', cnName: '创建决策'))
            repo.saveOrUpdate(new Permission(enName: 'decision-update', cnName: '更新决策'))
            repo.saveOrUpdate(new Permission(enName: 'field-add', cnName: '新增属性'))
            repo.saveOrUpdate(new Permission(enName: 'field-update', cnName: '更新属性'))
            repo.saveOrUpdate(new Permission(enName: 'field-del', cnName: '删除属性'))
            repo.saveOrUpdate(new Permission(enName: 'dataCollector-add', cnName: '新增收集器'))
            repo.saveOrUpdate(new Permission(enName: 'dataCollector-update', cnName: '更新收集器'))
            repo.saveOrUpdate(new Permission(enName: 'dataCollector-del', cnName: '删除收集器'))
        }

//        def u = repo.find(User){root, query, cb -> cb.equal(root.get('name'), 'admin')}
//        u.permissions = repo.findList(Permission).collect {it.enName}.join(",")
//        repo.saveOrUpdate(u)

        if (repo.count(User) < 1) {
            log.info("初始化默认用户 admin")
            repo.saveOrUpdate(new User(name: 'admin', password: 'admin', permissions: repo.findList(Permission, null).collect {it.enName}.join(",")))
        }
    }
}

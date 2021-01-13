package service.rule

import cn.xnatural.app.ServerTpl
import cn.xnatural.enet.event.EC
import cn.xnatural.enet.event.EL
import cn.xnatural.jpa.Repo
import cn.xnatural.remoter.Remoter
import entity.Decision
import entity.Permission
import entity.User

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
        [// 默认静态权限
         new Permission(enName: 'grant', cnName: '权限分配'),
         new Permission(enName: 'mnt-login', cnName: '用户登陆'),
         new Permission(enName: 'user-add', cnName: '新增用户'),
         new Permission(enName: 'user-del', cnName: '删除用户'),
         new Permission(enName: 'password-reset', cnName: '密码重置'),
         new Permission(enName: 'decision-read', cnName: '查看决策'),
         new Permission(enName: 'decision-add', cnName: '创建决策'),
         // new Permission(enName: 'decision-del', cnName: '删除决策'),
         // new Permission(enName: 'decision-update', cnName: '更新决策'),
         new Permission(enName: 'field-read', cnName: '查看字段'),
         new Permission(enName: 'field-add', cnName: '新增字段'),
         new Permission(enName: 'field-update', cnName: '更新字段'),
         new Permission(enName: 'field-del', cnName: '删除字段'),
         new Permission(enName: 'dataCollector-read', cnName: '查看收集器'),
         new Permission(enName: 'dataCollector-add', cnName: '新增收集器'),
         new Permission(enName: 'dataCollector-update', cnName: '更新收集器'),
         new Permission(enName: 'dataCollector-del', cnName: '删除收集器'),
         new Permission(enName: 'opHistory-read', cnName: '查看操作历史'),
         new Permission(enName: 'decisionResult-read', cnName: '查看决策结果'),
         new Permission(enName: 'collectResult-read', cnName: '查看收集记录')
        ].each { p ->
            // ConstraintViolationException
            if (!repo.find(Permission) { root, query, cb -> cb.equal(root.get("enName"), p.enName) }) {
                repo.saveOrUpdate(p)
                log.info("添加默认静态权限: " + p.enName + ", " + p.cnName)
            }
        }

        // 添加历史未添加的权限
        for (int i = 0, limit = 30; true; i++) {
            def ls = repo.findList(Decision, i*limit, limit)
            if (!ls) break
            ls.each { decision ->
                if (!repo.count(Permission) {root, query, cb -> cb.equal(root.get("mark"), decision.id)}) { // 决策权限不存在,则创建
                    [ // 一个决策对应的所有权限
                       new Permission(enName:  "decision-update-" + decision.id, cnName: "更新决策:" + decision.name, mark: decision.id),
                       new Permission(enName:  "decision-del-" + decision.id, cnName: "删除决策:" + decision.name, mark: decision.id),
                       new Permission(enName:  "decision-read-" + decision.id, cnName: "查看决策:" + decision.name, mark: decision.id)
                    ].each {repo.saveOrUpdate(it)}
                }
            }
        }

        [// 初始化默认用户
         new User(name: 'admin', password: 'admin', permissions: repo.findList(Permission, null).collect {it.enName}.join(","))
        ].each {u ->
            if (!repo.find(User) {root, query, cb -> cb.equal(root.get("name"), u.name)}) {
                repo.saveOrUpdate(u)
                log.info("添加默认用户. " + u.name)
            }
        }
    }


    /**
     * 决策变化时, 更新决策相关权限
     * @param ec
     * @param decisionId
     */
    @EL(name = ['decisionChange'], async = true)
    protected void listenDecisionChange(EC ec, String id) {
        if (!id) return
        if ((ec?.source() == bean(Remoter))) return
        def decision = repo.find(Decision) { root, query, cb -> cb.equal(root.get('id'), id)}
        if (decision == null) { //删除
            repo.findList(Permission) { root, query, cb -> cb.equal(root.get("mark"), id)}
                    ?.each {p ->
                        repo.delete(p)
                        int start = 0 //删除用户关联的权限
                        do {
                            def ls = repo.findList(User, (start++) * 10, 10) {root, query, cb -> cb.like(root.get("permissions"), "%" + p.enName + "%")}
                            if (!ls) break
                            ls.each {u ->
                                u.permissions = u.permissions.split(",").findAll {it != p.enName}.join(",")
                                repo.saveOrUpdate(u)
                            }
                        } while (true)
                    }
        } else {
            def ps = [ // 一个决策对应的所有权限
                       new Permission(enName:  "decision-update-" + decision.id, cnName: "更新决策:" + decision.name, mark: decision.id),
                       new Permission(enName:  "decision-del-" + decision.id, cnName: "删除决策:" + decision.name, mark: decision.id),
                       new Permission(enName:  "decision-read-" + decision.id, cnName: "查看决策:" + decision.name, mark: decision.id)
            ]
            def u = decision.creator ? repo.find(User) {root, query, cb -> cb.equal(root.get("name"), decision.creator)} : null
            if (u) { // 更新创建者的权限
                LinkedHashSet ls = new LinkedHashSet(u.permissions?.split(",")?.toList()?:Collections.emptyList())
                ps.each {ls.add(it.enName)}
                u.permissions = ls.join(",")
                repo.saveOrUpdate(u)
            }
            repo.findList(Permission) { root, query, cb -> cb.equal(root.get("mark"), decision.id)}
                    ?.each {p ->
                        if (p.enName.startsWith("decision-update-")) {
                            p.cnName = "更新决策:" + decision.name
                        } else if (p.enName.startsWith("decision-del-")) {
                            p.cnName = "删除决策:" + decision.name
                        } else if (p.enName.startsWith("decision-read-")) {
                            p.cnName = "查看决策:" + decision.name
                        }
                        repo.saveOrUpdate(p)
                        ps.removeIf {it.enName == p.enName}
                    }
            // 创建新决策权限
            ps?.each {repo.saveOrUpdate(it)}
        }
    }
}

package ctrl

import cn.xnatural.app.ServerTpl
import cn.xnatural.http.ApiResp
import cn.xnatural.http.Ctrl
import cn.xnatural.http.HttpContext
import cn.xnatural.http.Path
import cn.xnatural.jpa.Repo
import entity.Decision
import service.rule.DecisionManager

import java.text.SimpleDateFormat

@Ctrl(prefix = 'mnt')
class MntAnalyseCtrl extends ServerTpl {
    @Lazy def repo = bean(Repo, 'jpa_rule_repo')

    @Path(path = 'countDecide')
    ApiResp countDecide(HttpContext hCtx, String startTime, String endTime, String type) {
        Date start = startTime ? new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(startTime) : null
        Date end = endTime ? new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(endTime) : null
        def cal = Calendar.getInstance()
        if (type == 'today') {
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        } else if (type == 'lastOneHour') {
            cal.add(Calendar.HOUR_OF_DAY, -1)
        } else if (type == 'lastTwoHour') {
            cal.add(Calendar.HOUR_OF_DAY, -2)
        } else if (type == 'lastFiveHour') {
            cal.add(Calendar.HOUR_OF_DAY, -5)
        } else return ApiResp.fail("type: '$type' unkonwn")
        def ids = hCtx.getSessionAttr("permissions").split(",").findResults {String p -> p.replace("decision-read-", "").replace("decision-read", "")}.findAll {it}
        if (!ids) return ApiResp.ok()
        ids = repo.findList(Decision) {root, query, cb -> root.get("id").in(ids)}.findResults {it.decisionId}
        if (!ids) return ApiResp.ok()
        ApiResp.ok(repo.rows(
                "select decision_id, decision, count(1) total from decision_result where occur_time>=:time and decision_id in (:ids) group by decision_id, decision",
                cal.getTime(), ids
        ).collect {Map<String, Object> record ->
            def data = new HashMap<>(5)
            record.each {e ->
                data.put(e.key.toLowerCase(), e.value)
            }
            data['decisionName'] = bean(DecisionManager).findDecision(data['decision_id']).spec.决策名
            data
        })
    }
}

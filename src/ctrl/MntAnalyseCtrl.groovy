package ctrl

import cn.xnatural.app.ServerTpl
import cn.xnatural.http.ApiResp
import cn.xnatural.http.Ctrl
import cn.xnatural.http.HttpContext
import cn.xnatural.http.Path
import cn.xnatural.jpa.Page
import cn.xnatural.jpa.Repo
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import entity.DecisionResult
import service.rule.DecisionEnum
import service.rule.DecisionManager

import java.text.SimpleDateFormat

@Ctrl(prefix = 'mnt')
class MntAnalyseCtrl extends ServerTpl {
    @Lazy def repo = bean(Repo, 'jpa_rule_repo')
    @Lazy def decisionManager = bean(DecisionManager)


    /**
     * 统计一段时间的决策结果
     * @param startTime required yyyy-MM-dd HH:mm:ss
     * @param endTime yyyy-MM-dd HH:mm:ss
     */
    @Path(path = 'countDecide')
    ApiResp countDecide(HttpContext hCtx, String startTime, String endTime) {
        if (startTime == null) return ApiResp.fail("Param startTime required")
        Date start = startTime ? new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(startTime) : null
        Date end = endTime ? new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(endTime) : null
        def ids = hCtx.getSessionAttr("permissions").split(",")
            .findAll {String p -> p.startsWith("decision-read-")}
            .findResults {String p -> p.replace("decision-read-", "")}
            .findAll {it}
        if (!ids) return ApiResp.ok()
        hCtx.response.cacheControl(2) // 缓存2秒
        String sql = """
            select t1.decision_id, t2.name decisionName, t1.decision, count(1) total from ${repo.tbName(DecisionResult).replace("`", '')} t1
            left join decision t2 on t1.decision_id = t2.id
            where t1.decision is not null and t1.occur_time>=:start${end ? " and t1.occur_time<=:end" : ""} and t1.decision_id in (:ids) group by t1.decision_id, t1.decision
        """.trim()
        ApiResp.ok(end ? repo.rows(sql, start, end, ids) : repo.rows(sql, start, ids))
    }


    /**
     * 统计一段时间的规则结果
     * @param decisionId 决策id
     * @param startTime required yyyy-MM-dd HH:mm:ss
     * @param endTime yyyy-MM-dd HH:mm:ss
     */
    @Path(path = 'countRule')
    ApiResp countRule(HttpContext hCtx, String decisionId, String startTime, String endTime) {
        if (startTime == null) return ApiResp.fail("Param startTime required")
        Date start = startTime ? new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(startTime) : null
        Date end = endTime ? new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(endTime) : null
        def ids = hCtx.getSessionAttr("permissions").split(",")
            .findAll {String p -> p.startsWith("decision-read-")}
            .findResults {String p -> p.replace("decision-read-", "")}
            .findAll {it}
        if (!ids) return ApiResp.ok().desc("无可查看的决策")
        ids = decisionId ? ids.findAll {it == decisionId} : ids
        if (!ids) return ApiResp.ok().desc("无可查看的决策")
        hCtx.response.cacheControl(5) // 缓存5秒
        String sql = """
            select t1.decision_id decisionId, t2.name decisionName, t1.rules from ${repo.tbName(DecisionResult).replace("`", '')} t1
            left join decision t2 on t1.decision_id = t2.id
            where t1.decision is not null and t1.rules is not null and t1.occur_time>=:start${end ? " and t1.occur_time<=:end" : ""} and t1.decision_id in (:ids)
        """.trim()
        def ls = [] as LinkedList
        for (int page = 1, pageSize = 200; ;page++) {
            Page rPage = end ? repo.sqlPage(sql, page, pageSize, start, end, ids) : repo.sqlPage(sql, page, pageSize, start, ids)
            ls.addAll(rPage.list)
            if (page >= rPage.totalPage) break
        }
        // decision_id, decisionName, ruleName, decision, total
        ApiResp.ok(
            ls.findResults {Map<String, String> e ->
                JSON.parseArray(e['rules']).findResults { JSONObject jo ->
                    e['decisionId'] + '||' + e['decisionName'] + '||' + jo['attrs']['规则名'] + '||' + (jo['decision']?:DecisionEnum.Accept)
                }
            }.flatten().countBy {it}.findResults {e ->
                def arr = e.key.split("\\|\\|")
                return [decisionId: arr[0], decisionName: arr[1], ruleName: arr[2], decision: arr[3], total: e.value]
            }
        )
    }
}

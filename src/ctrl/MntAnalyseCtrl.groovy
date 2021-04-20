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
import entity.DecideRecord
import service.rule.DecideResult
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
            select t1.decision_id, t2.name decisionName, t1.result, count(1) total from ${repo.tbName(DecideRecord).replace("`", '')} t1
            left join decision t2 on t1.decision_id = t2.id
            where t1.result is not null and t1.occur_time>=:start${end ? " and t1.occur_time<=:end" : ""} and t1.decision_id in (:ids) 
            group by t1.decision_id, t1.result
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
            select t1.decision_id decisionId, t2.name decisionName, t1.detail from ${repo.tbName(DecideRecord).replace("`", '')} t1
            left join decision t2 on t1.decision_id = t2.id
            where t1.result is not null and t1.detail is not null and t1.occur_time>=:start${end ? " and t1.occur_time<=:end" : ""} and t1.decision_id in (:ids)
        """.trim()
        def ls = [] as LinkedList
        for (int page = 1, pageSize = 200; ;page++) {
            Page rPage = end ? repo.sqlPage(sql, page, pageSize, start, end, ids) : repo.sqlPage(sql, page, pageSize, start, ids)
            ls.addAll(rPage.list)
            if (page >= rPage.totalPage) break
        }
        // decisionId, decisionName, policyName, ruleName, result, total
        ApiResp.ok(
            ls.findResults {Map<String, String> record ->
                JSON.parseObject("detail")?.getJSONArray("policies")?.findResults {JSONObject pJo ->
                    pJo.getJSONArray("rules").findResults { JSONObject rJo ->
                        record['decisionId'] + '||' + record['decisionName'] + '||' + pJo['attr']['策略名']  +'||' + rJo['attrs']['规则名'] + '||' + (rJo['result']?:DecideResult.Accept)
                    }
                }?.flatten()
            }.flatten().countBy {it}.findResults {e ->
                def arr = e.key.split("\\|\\|")
                return [decisionId: arr[0], decisionName: arr[1], policyName: arr[2], ruleName: arr[3], result: arr[4], total: e.value]
            }.sort {o1, o2 ->
                // 把拒绝多的排前面
                if (o1['result'] == "Reject" && o2['result'] == "Reject") return o2['total'] - o1['total']
                else if (o1['decision'] == "Reject") return -1
                else if (o2['decision'] == "Reject") return 1
                else return 0
            }.takeRight(decisionId ? Integer.MAX_VALUE : 400) // 如果是指定某个决策, 则全部显示, 如果是查所有则限制显示(有可能会得多)
        )
    }
}

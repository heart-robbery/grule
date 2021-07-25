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
        def ids = hCtx.getAttr("permissions", Set)
            .findAll {String p -> p.startsWith("decision-read-")}
            .findResults {String p -> p.replace("decision-read-", "")}
            .findAll {it}
        if (!ids) return ApiResp.ok()
        hCtx.response.cacheControl(2) // 缓存2秒
        String sql = """
            select t1.decision_id, t2.name decisionName, t1.result, count(1) total from ${repo.tbName(DecideRecord).replace("`", '')} t1
            left join decision t2 on t1.decision_id = t2.id
            where t1.occur_time>=:start${end ? " and t1.occur_time<=:end" : ""} and t1.decision_id in (:ids) and t1.result is not null 
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
        def ids = hCtx.getAttr("permissions", Set)
            .findAll {String p -> p.startsWith("decision-read-")}
            .findResults {String p -> p.replace("decision-read-", "")}
            .findAll {it}
        if (!ids) return ApiResp.ok().desc("无可查看的决策")
        ids = decisionId ? ids.findAll {it == decisionId} : ids
        if (!ids) return ApiResp.ok().desc("无可查看的决策")
        hCtx.response.cacheControl(60) // 缓存60秒

        // mysql 8.0.4+ or mariaDb 10.6+ 有json_table函数
        def verArr = repo.getDBVersion().split("\\.")
        if (
            (repo.dialect.containsIgnoreCase("mysql") && verArr[0].toInteger() >= 8 && verArr[2].toInteger() >= 4) ||
            (repo.dialect.containsIgnoreCase("maria") && verArr[0].toInteger() >= 10 && verArr[1].toInteger() >= 6)
        ) {
            String sql = """
                SELECT
                    t1.decision_id decisionId, t3.name decisionName, t2.policyName, t2.ruleName, t2.result, count(1) total
                FROM decide_record t1
                join json_table(JSON_SET(t1.detail, '\$.id', t1.id),
                    '\$' COLUMNS (
                        id varchar(50) path '\$.id',
                        NESTED PATH '\$.policies[*]' COLUMNS (
                            policyName varchar(200) path '\$.attrs."策略名"',
                                NESTED PATH '\$.rules[*]' COLUMNS (
                                    ruleName varchar(200) path '\$.attrs."规则名"',
                                    result varchar(20) path '\$.result'
                        )))
                ) t2 on t2.id=t1.id
                left join decision t3 on t1.decision_id = t3.id
                where
                    t1.occur_time>=:start${end ? " and t1.occur_time<=:end" : ""} and t1.decision_id in (:ids)
                    and t1.detail is not null and t1.result is not null
                group by decisionId, policyName, ruleName, result
                order by case t2.result when 'Reject' then 3 when 'Review' then 2 when 'Accept' then 1 when isnull(t2.result) then 0 end desc, total desc
                limit 30
            """
            return ApiResp.ok(end ? repo.rows(sql, start, end, ids) : repo.rows(sql, start, ids))
        }

        String sql = """
            select t1.decision_id decisionId, t2.name decisionName, t1.detail 
            from ${repo.tbName(DecideRecord).replace("`", '')} t1
            left join decision t2 on t1.decision_id = t2.id
            where 
                t1.occur_time>=:start${end ? " and t1.occur_time<=:end" : ""} and t1.decision_id in (:ids)
                and t1.detail is not null and t1.result is not null
        """.trim()

        def ls = [] as LinkedList
        for (int page = 1, pageSize = 100; ;page++) {
            Page rPage = end ? repo.sqlPage(sql, page, pageSize, start, end, ids) : repo.sqlPage(sql, page, pageSize, start, ids)
            ls.addAll(rPage.list)
            if (page >= rPage.totalPage) break
        }
        // decisionId, decisionName, policyName, ruleName, result, total
        ApiResp.ok(
            ls.findResults {Map<String, String> record ->
                record["detail"] ? JSON.parseObject(record["detail"])?.getJSONArray("policies")?.findResults {JSONObject pJo ->
                    pJo.getJSONArray("items")?.findAll {JSONObject rJo -> rJo['attrs']['规则名']}?.findResults { JSONObject rJo ->
                        record['decisionId'] + '||' + record['decisionName'] + '||' + pJo['attrs']['策略名'] +'||' + rJo['attrs']['规则名'] + '||' + (rJo['result']?:DecideResult.Accept)
                    }?:[]
                }?.flatten() : []
            }.flatten().countBy {it}.findResults {e ->
                def arr = e.key.split("\\|\\|")
                return [decisionId: arr[0], decisionName: arr[1], policyName: arr[2], ruleName: arr[3], result: arr[4], total: e.value]
            }.sort {o1, o2 ->
                // 把拒绝多的排前面
                if (o1['result'] == "Reject" && o2['result'] == "Reject") return o2['total'] - o1['total']
                else if (o1['result'] == "Reject") return -1
                else if (o2['result'] == "Reject") return 1
                else return 0
            }.takeRight(decisionId ? Integer.MAX_VALUE : 30) // 如果是指定某个决策, 则全部显示, 如果是查所有则限制显示(有可能会得多)
        )
    }
}

package ctrl

import cn.xnatural.app.ServerTpl
import cn.xnatural.http.ApiResp
import cn.xnatural.http.Ctrl
import cn.xnatural.http.HttpContext
import cn.xnatural.http.Path
import cn.xnatural.jpa.Repo
import entity.Decision
import entity.DecisionResult
import service.rule.DecisionManager

import java.text.SimpleDateFormat

@Ctrl(prefix = 'mnt')
class MntAnalyseCtrl extends ServerTpl {
    @Lazy def repo = bean(Repo, 'jpa_rule_repo')
    @Lazy def decisionManager = bean(DecisionManager)


    /**
     * 统计一段时间工决策结果
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
            select decision_id, decision, count(1) total from ${repo.tbName(DecisionResult).replace("`", '')} 
            where decision is not null and occur_time>=:start${end ? " and occur_time<=:end" : ""} and decision_id in (:ids) group by decision_id, decision
        """
        ApiResp.ok((end ? repo.rows(sql, start, end, ids) : repo.rows(sql, start, ids)).collect {Map<String, Object> record ->
            def data = new HashMap<>(5)
            record.each {e ->
                data.put(e.key.toLowerCase(), e.value)
            }
            data['decisionName'] = decisionManager.decisionMap.find {it.value.decision.id == data['decision_id']}?.value?.decision?.name
            data
        })
    }
}

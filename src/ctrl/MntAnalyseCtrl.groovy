package ctrl

import cn.xnatural.http.ApiResp
import cn.xnatural.http.Ctrl
import cn.xnatural.http.Path
import cn.xnatural.jpa.Repo
import core.ServerTpl
import org.hibernate.query.internal.NativeQueryImpl
import org.hibernate.transform.Transformers
import service.rule.DecisionManager

import java.text.SimpleDateFormat

@Ctrl(prefix = 'mnt')
class MntAnalyseCtrl extends ServerTpl {
    @Lazy def repo = bean(Repo, 'jpa_rule_repo')

    @Path(path = 'countDecide')
    ApiResp countDecide(String startTime, String endTime, String type) {
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
        } else return ApiResp.fail("type: '$type' not supprot")
        ApiResp.ok(repo.trans{se ->
            se.createNativeQuery(
                    "select decision_id, decision, count(*) total from decision_result where occur_time>=:occurTime group by decision_id, decision"
            )
                    .setParameter("occurTime", cal.getTime())
                    .unwrap(NativeQueryImpl).setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP)
                    .list()
        }.collect {Map<String, Object> record ->
            def data = new HashMap<>(5)
            record.each {e ->
                data.put(e.key.toLowerCase(), e.value)
            }
            data['decisionName'] = bean(DecisionManager).findDecision(data['decision_id']).spec.决策名
            data
        })
    }
}

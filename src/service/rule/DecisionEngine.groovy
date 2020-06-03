package service.rule

import cn.xnatural.enet.event.EL
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature
import core.module.OkHttpSrv
import core.module.ServerTpl

/**
 * 决策执行引擎
 */
class DecisionEngine extends ServerTpl {
    @Lazy def dm = bean(DecisionManager)
    @Lazy def pm = bean(PolicyManger)
    @Lazy def am = bean(AttrManager)
    @Lazy def http = bean(OkHttpSrv)


    @EL(name = 'decision.end', async = true)
    void endDecision(DecisionContext ctx) {
        log.info("end decision: " + JSON.toJSONString(ctx.summary(), SerializerFeature.WriteMapNullValue))
    }


    /**
     * 执行决策
     * @param decisionId
     * @param async 是否异步
     * @param id id
     * @param params 参数
     * @return
     */
    Map run(String decisionId, boolean async = true, String id = null, Map params = []) {
        DecisionContext ctx = new DecisionContext()
        def ds = dm.findDecision(decisionId)
        if (ds == null) throw new IllegalArgumentException("未找到决策: " + decisionId)
        ctx.setDs(ds)
        ctx.setId(id?:UUID.randomUUID().toString().replaceAll('-', ''))
        ctx.setPm(pm)
        ctx.setAm(am)
        ctx.setEp(ep)
        ctx.setInput(params)
        // ctx.setExec(exec)

        log.info("Run decision. decisionId: " + decisionId + ", async: " + async + ", id: " + ctx.getId() + ", params: " + params)
        if (async) super.async {
            ctx.start()
            String cbUrl = params['callback'] // 回调Url
            if (cbUrl && cbUrl.startsWith('http')) {
                http.post(cbUrl).jsonBody(JSON.toJSONString(ctx.result()), SerializerFeature.WriteMapNullValue).execute()
            }
        }
        else ctx.start()
        return ctx.result()
    }
}

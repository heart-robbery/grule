package service.rule

import cn.xnatural.enet.event.EL
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature
import core.OkHttpSrv
import core.ServerTpl

/**
 * 决策执行引擎
 */
class DecisionEngine extends ServerTpl {
    @Lazy def dm = bean(DecisionManager)
    @Lazy def pm = bean(PolicyManger)
    @Lazy def am = bean(AttrManager)
    @Lazy def http = bean(OkHttpSrv)


    // 决策执行结果监听
    @EL(name = 'decision.end', async = true)
    void endDecision(DecisionContext ctx) {
        log.info("end decision: " + JSON.toJSONString(ctx.summary(), SerializerFeature.WriteMapNullValue))
        // TODO 保存决策结果

        if (ctx.input['async'] == 'true' ? true : false) { // 异步查询的,主动回调通知
            String cbUrl = ctx.input['callback'] // 回调Url
            if (cbUrl && cbUrl.startsWith('http')) {
                http.post(cbUrl).jsonBody(JSON.toJSONString(ctx.result(), SerializerFeature.WriteMapNullValue)).execute()
            }
        }
    }


    /**
     * 执行决策
     * @param decisionId
     * @param async 是否异步
     * @param id id
     * @param params 参数
     * @return
     */
    Map<String, Object> run(String decisionId, boolean async = true, String id = null, Map<String, Object> params = []) {
        def decision = dm.findDecision(decisionId)
        if (decision == null) throw new IllegalArgumentException("未找到决策: " + decisionId)
        DecisionContext ctx = new DecisionContext()
        ctx.setDecisionSpec(decision)
        ctx.setId(id?:UUID.randomUUID().toString().replaceAll('-', ''))
        ctx.setAttrManager(am)
        ctx.setEp(ep)
        ctx.setInput(params)

        log.info("Run decision. decisionId: " + decisionId + ", id: " + ctx.getId() + ", async: " + async  + ", params: " + params)
        if (async) super.async { ctx.start() }
        else ctx.start()
        return ctx.result()
    }
}

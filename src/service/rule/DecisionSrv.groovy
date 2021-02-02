package service.rule

import cn.xnatural.app.ServerTpl
import cn.xnatural.enet.event.EL
import cn.xnatural.jpa.Repo
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature
import core.OkHttpSrv
import entity.CollectResult
import entity.DecisionResult

import java.time.Duration

/**
 * 决策Service
 */
class DecisionSrv extends ServerTpl {
    static final String SAVE_RESULT = 'save_result'

    @Lazy def http = bean(OkHttpSrv)
    @Lazy def repo = bean(Repo, 'jpa_rule_repo')


    @EL(name = 'sys.starting', async = true)
    protected void init() {
        Long lastWarn // 上次告警时间
        queue(SAVE_RESULT)
            .failMaxKeep(getInteger(SAVE_RESULT + ".failMaxKeep", 10000))
            .parallel(getInteger("saveResult.parallel", 2))
            .errorHandle {ex, devourer ->
                if (lastWarn == null || (System.currentTimeMillis() - lastWarn >= Duration.ofSeconds(getLong(SAVE_RESULT + ".warnInterval", 60 * 5L)).toMillis())) {
                    lastWarn = System.currentTimeMillis()
                    log.error("保存决策结果到数据库错误", ex)
                    ep.fire("globalMsg", "保存决策结果到数据库错误: " + (ex.message?:ex.class.simpleName))
                }
                Thread.sleep(500 + new Random().nextInt(1000))
            }
    }


    /**
     * 清理过期CollectResult数据
     */
    long cleanCollectResult() {
        long cleanTotal = 0
        def keepCount = getLong("collectResult.keepCount", 0)
        if (keepCount > 0) { //保留多少条数据
            def total = repo.count(CollectResult)
            while (total > keepCount) {
                def cr = repo.find(CollectResult) {root, query, cb -> query.orderBy(cb.desc(root.get("collectDate")))}
                repo.delete(cr)
                total--; cleanTotal++
                log.info("Deleted expire collectResult data: {}", JSON.toJSONString(cr))
            }
        }
        def keepDay = getInteger("collectResult.keepDay", 0)
        if (keepDay > 0) { //保留多天的数据
            def cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_MONTH, -keepDay)
            do {
                def cr = repo.find(CollectResult) {root, query, cb -> query.orderBy(cb.desc(root.get("collectDate")))}
                if (cr.collectDate == null || cr.collectDate < cal.time) {
                    repo.delete(cr)
                    cleanTotal++
                    log.info("Deleted expire collectResult data: {}", JSON.toJSONString(cr))
                } else break
            } while (true)
        }
        return cleanTotal
    }


    /**
     * 计划清理过期DecisionResult数据
     */
    long cleanDecisionResult() {
        long cleanTotal = 0
        def keepCount = getLong("decisionResult.keepCount", 0)
        if (keepCount > 0) { //保留多少条数据
            def total = repo.count(DecisionResult)
            while (total > keepCount) {
                def dr = repo.find(DecisionResult) {root, query, cb -> query.orderBy(cb.desc(root.get("occurTime")))}
                repo.delete(dr)
                total--; cleanTotal++
                log.info("Deleted expire decisionResult data: {}", JSON.toJSONString(dr))
            }
        }
        def keepDay = getInteger("decisionResult.keepDay", 0)
        if (keepDay > 0) { //保留多天的数据
            def cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_MONTH, -keepDay)
            do {
                def dr = repo.find(DecisionResult) {root, query, cb -> query.orderBy(cb.desc(root.get("occurTime")))}
                if (dr.occurTime == null || dr.occurTime < cal.time) {
                    repo.delete(dr)
                    cleanTotal++
                    log.info("Deleted expire decisionResult data: {}", JSON.toJSONString(dr))
                } else break
            } while (true)
        }
        return cleanTotal
    }




    /**
     * 系统全局消息
     * @param msg
     */
    @EL(name = 'globalMsg', async = true)
    void globalMsg(String msg) {
        log.info("系统消息: " + msg)
        ep.fire("wsMsg_rule", msg)
        String url = getStr('msgNotifyUrl', null)
        if (url) {
            //bean(OkHttpSrv)?.
        }
    }


    // 决策执行结果监听
    @EL(name = 'decision.end', async = true)
    void endDecision(DecisionContext ctx) {
        log.info("end decision: " + JSON.toJSONString(ctx.summary(), SerializerFeature.WriteMapNullValue))

        super.async { // 异步查询的, 异步回调通知
            if (Boolean.valueOf(ctx.input.getOrDefault('async', false).toString())) {
                String cbUrl = ctx.input['callback'] // 回调Url
                if (cbUrl && cbUrl.startsWith('http')) {
                    (1..2).each {
                        try {
                            http.post(cbUrl).jsonBody(JSON.toJSONString(ctx.result(), SerializerFeature.WriteMapNullValue)).execute()
                        } catch (ex) {
                            log.error("回调失败. id: " + ctx.id + ", url: " + cbUrl, ex)
                        }
                    }
                }
            }
        }

        queue(SAVE_RESULT) { // 保存决策结果到数据库
            repo.saveOrUpdate(
                repo.findById(DecisionResult, ctx.id).tap {
                    def keyPropName = getStr("keyPropName", null)
                    if (keyPropName) {
                        keyProp = ctx.summary().get('attrs')[(keyPropName)]
                    }
                    status = ctx.status
                    exception = ctx.summary().get('exception')
                    decision = ctx.summary().get('decision')
                    spend = ctx.summary().get('spend')
                    attrs = JSON.toJSONString(ctx.summary().get('attrs'), SerializerFeature.WriteMapNullValue)

                    def rs = ctx.summary().get('rules')
                    if (rs) rules = JSON.toJSONString(rs, SerializerFeature.WriteMapNullValue)

                    def dcr = ctx.summary().get('dataCollectResult')
                    if (dcr) dataCollectResult = JSON.toJSONString(dcr, SerializerFeature.WriteMapNullValue)
                }
            )
        }
    }
}

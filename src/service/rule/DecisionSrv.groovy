package service.rule

import cn.xnatural.app.ServerTpl
import cn.xnatural.enet.event.EL
import cn.xnatural.jpa.Repo
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature
import core.OkHttpSrv
import entity.DecideRecord

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
            .parallel(getInteger("saveResult.parallel", 5))
            .errorHandle {ex, me ->
                if (lastWarn == null || (System.currentTimeMillis() - lastWarn >= Duration.ofSeconds(getLong(SAVE_RESULT + ".warnInterval", 60 * 3L)).toMillis())) {
                    lastWarn = System.currentTimeMillis()
                    log.error("保存决策结果到数据库错误", ex)
                    ep.fire("globalMsg", "保存决策结果到数据库错误: " + (ex.message?:ex.class.simpleName))
                }
                // 暂停一会
                me.suspend(Duration.ofMillis(500 + new Random().nextInt(1000)))
            }
    }


    /**
     * 系统全局消息
     * @param msg
     */
    @EL(name = 'globalMsg', async = true)
    void globalMsg(String msg) {
        log.info("系统消息: " + msg)
        ep.fire("wsMsg_rule", msg)
        String url = getStr('ddMsgNotifyUrl', null)
        if (url) {
            http.post(url).jsonBody(JSON.toJSONString([
                    msgtype: "text",
                    text: ["content": "RULE(${app().profile}): $msg".toString()],
                    at: ["isAtAll": false]
            ])).debug().execute()
        }
    }


    // 决策执行结果监听
    @EL(name = 'decision.end', async = true)
    void endDecision(DecisionContext ctx) {
        log.info("end decision: " + JSON.toJSONString(ctx.summary(), SerializerFeature.WriteMapNullValue))

        // 异步查询的, 异步回调通知
        if (Boolean.valueOf(ctx.input.getOrDefault('async', false).toString())) {
            async {
                String cbUrl = ctx.input['callback'] // 回调Url
                if (cbUrl && cbUrl.startsWith('http')) {
                    def result = ctx.result()
                    (1..getInteger("callbackMaxTry", 2)).each {
                        try {
                            http.post(cbUrl).jsonBody(JSON.toJSONString(result, SerializerFeature.WriteMapNullValue)).debug().execute()
                        } catch (ex) {
                            log.error("回调失败. id: " + ctx.id + ", url: " + cbUrl, ex)
                        }
                    }
                }
            }
        }

        // 保存决策结果到数据库
        queue(SAVE_RESULT) {
            repo.saveOrUpdate(
                    repo.findById(DecideRecord, ctx.id).tap {
                        status = ctx.status
                        exception = ctx.summary()['exception']
                        result = ctx.summary()['result']
                        spend = ctx.summary()['spend']
                        data = JSON.toJSONString(ctx.summary()['data'], SerializerFeature.WriteMapNullValue)
                        detail = JSON.toJSONString(ctx.summary()['detail'], SerializerFeature.WriteMapNullValue)
                        def dr = ctx.summary()['dataCollectResult']
                        if (dr) dataCollectResult = JSON.toJSONString(dr, SerializerFeature.WriteMapNullValue)
                    }
            )
        }
    }


    /**
     * 计划清理过期DecideRecord数据
     */
    long cleanDecideRecord() {
        long cleanTotal = 0

        // DecideRecord 清理函数
        def clean = {DecideRecord dr ->
            int count = repo.trans { session ->
                // 删除关联的 收集器记录
                session.createQuery("delete from CollectRecord where decideId=:decideId")
                        .setParameter("decideId", dr.id).executeUpdate()
                session.createQuery("delete from DecideRecord where id=:id")
                        .setParameter("id", dr.id).executeUpdate()
            }
            cleanTotal += count
            log.info("Deleted expire decideRecord data: {}", JSON.toJSONString(dr))
        }

        //保留多少条数据. NOTE: 不能多个进程同时删除(多删)
        def keepCount = getLong("decideRecord.keepCount", 0)
        if (keepCount > 0) {
            def total = repo.count(DecideRecord)
            while (total > keepCount) {
                clean(repo.find(DecideRecord) { root, query, cb -> query.orderBy(cb.asc(root.get("occurTime")))})
                total--
            }
        }

        //保留多天的数据,如果 配置了decideRecord.keepCount 则不执行此清理
        def keepDay = getInteger("decideRecord.keepDay", 0)
        if (keepDay > 0 && !keepCount) {
            def cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_MONTH, -keepDay)
            do {
                def dr = repo.find(DecideRecord) { root, query, cb -> query.orderBy(cb.asc(root.get("occurTime")))}
                if (dr.occurTime == null || dr.occurTime < cal.time) clean(dr)
                else break
            } while (true)
        }
        return cleanTotal
    }
}

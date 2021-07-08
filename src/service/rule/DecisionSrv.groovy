package service.rule

import cn.xnatural.app.ServerTpl
import cn.xnatural.enet.event.EL
import cn.xnatural.jpa.Repo
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature
import core.OkHttpSrv
import entity.DecideRecord
import entity.Lock

import java.text.SimpleDateFormat
import java.time.Duration

/**
 * 决策Service
 */
class DecisionSrv extends ServerTpl {
    protected static final String SAVE_RESULT = 'save_result'

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


    @EL(name = 'sys.stopping', async = true)
    protected void stop() {
        // 尽量等到 对列中的 数据都持久化完成
        long start = System.currentTimeMillis()
        if (queue(SAVE_RESULT).waitingCount > 0) log.warn("等待决策结果数据保存完...")
        while (queue(SAVE_RESULT).waitingCount > 0 && System.currentTimeMillis() - start < 1000 * 60 * 2) {
            Thread.sleep(1000)
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
                if (!cbUrl?.startsWith('http')) return
                def result = JSON.toJSONString(ctx.result(), SerializerFeature.WriteMapNullValue)
                for (i in 0..< getInteger("callbackMaxTry", 2)) {
                    try {
                        http.post(cbUrl).jsonBody(result).debug().execute()
                        break
                    } catch (ex) {}
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
    void cleanDecideRecord() {
        def lock = new Lock(name: 'cleanDecideRecord', comment: "清理过期数据")
        // 执行清理逻辑
        def doClean = {
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
                for (long total = repo.count(DecideRecord); total > keepCount; total--) {
                    clean(repo.find(DecideRecord) { root, query, cb -> query.orderBy(cb.asc(root.get("occurTime")))})
                    if (cleanTotal % getInteger("deleteUnit", 10) == 0) {
                        total = repo.count(DecideRecord)
                    }
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
        try {
            repo.saveOrUpdate(lock)
            async {
                try {
                    def total = doClean()
                    ep.fire("globalMsg", "清理过期决策数据结束. 共计: " + total)
                } finally {
                    repo.delete(lock)
                }
            }
        } catch (ex) {
            def cause = ex
            while (cause != null) {
                if (cause.message.contains("Duplicate entry")) {
                    def exist = repo.find(Lock) {root, query, cb -> cb.equal(root.get("name"), lock.name)}
                    if (exist) {
                        throw new RuntimeException("清理中... 开始时间: " + new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').format(exist.createTime))
                    } else {
                        throw new RuntimeException("刚清理完")
                    }
                }
                cause = cause.cause
            }
            repo.delete(lock)
            throw ex
        }
    }
}

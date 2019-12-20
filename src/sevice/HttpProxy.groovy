package sevice

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import core.AppContext
import core.Utils
import core.module.OkHttpSrv
import core.module.ServerTpl
import core.module.jpa.BaseRepo
import dao.entity.HttpCfg

import javax.annotation.Resource
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.regex.Pattern

/**
 * 根据http接口配置 {@link HttpCfg} 调用接口
 */
class HttpProxy extends ServerTpl {

    /**
     * 查询字符串 ${attr}
     */
    final Pattern p = Pattern.compile('\\$\\{(?<attr>\\w+)\\}')

    @Resource
    BaseRepo repo
    @Resource
    OkHttpSrv okHttp
    @Resource
    AppContext app


    /**
     * http proxy 执行
     * @param name 接口标识名
     * @param params 执行上下文
     * @return
     */
    String proxy(String name, Map<String, Object> params, Consumer<String> okFn = null, Consumer<Exception> failFn = {throw it}) {
        def cfg = repo.find(HttpCfg, {root, query, cb -> cb.equal(root.get("name"), name)})
        if (cfg == null) throw new IllegalArgumentException("未找到接口($name)配置")

        if (params == null) params = new HashMap<>()

        // 预处理
        def exeCtx = [app: app, params: params, cfg: cfg]
        if (cfg.preProcess) {
            Utils.eval(cfg.preProcess, exeCtx)
            if (exeCtx['resp']) { // 如果预处理中的 resp 已有值 则直接返回(一般用于缓存)
                return exeCtx['resp']
            }
        }

        // 构建http
        Utils.Http h
        def url = reBuildUrl(cfg.getUrl(), params)
        if ('get'.equalsIgnoreCase(cfg.method)) {
            h = okHttp.get(url)
        } else if ('post'.equalsIgnoreCase(cfg.method)) {
            h = okHttp.post(url)
            if (cfg.contentType.compareToIgnoreCase('application/json')) {
                h.jsonBody(reBuildJsonBodyStr(cfg.requestBody))
            } else if (cfg.contentType.compareToIgnoreCase('multipart/form-data')) {
                // TODO
                throw new IllegalArgumentException("Not support content type $cfg.contentType")
            } else {
                buildParamMap(cfg.requestBody).each {h.param(it.key, it.value)}
            }
        } else throw new IllegalArgumentException("Not support http method '$cfg.method'")

        // 执行请求
        String resp
        if (cfg.exeProcess) resp = Utils.eval(cfg.exeProcess, exeCtx)
        else resp = h.execute(okFn, failFn)

        // 请求后处理
        if (cfg.resultProcess) resp = Utils.eval(cfg.resultProcess, exeCtx)
        return resp
    }


    /**
     * 根据url模板字符串 建构 url
     * @param urlTpl
     * @param params
     * @return
     */
    protected String reBuildUrl(String urlTpl, Map<String, Object> params) {
        def m = p.matcher(urlTpl)
        if (!m.find()) return urlTpl

        // url 折成 地址 和 查询字符串 两部分
        def arr = urlTpl.split("\\?")
        return format(arr[0], params) + ((arr.length > 1 && arr[1] != null && !arr[1].isEmpty()) ? ("?" + reBuildQueryStr(arr[1])) : '')
    }


    /**
     * 请求json body 值替换
     * @param jsonStr
     * @param params
     * @return
     */
    protected String reBuildJsonBodyStr(String jsonStr, Map<String, Object> params) {
        if (jsonStr == null || jsonStr.isEmpty()) return jsonStr
        def rJo = new JSONObject()
        JSON.parseObject(jsonStr).each {
            def v = it.value
            if (v == null) return
            def m = p.matcher(it.value)
            if (m.find()) {v = params.get(m.group("attr"))}
            if (v != null) rJo.put(it.key, v)
        }
        return rJo.toString()
    }


    /**
     * a=${a}&b=xxx 中的变量设值, 并把变量不存存或为null的删除
     * 例1:
     *  a=${a}&b=xxx, ("a", "111") 最终返回 a=111&b=xxx
     * 例2:
     *  a=${a}&b=xxx, ("c", "111") 最终返回 b=xxx
     * @param qStr
     * @param params
     * @return
     */
    protected String reBuildQueryStr(String qStr, Map<String, Object> params) {
        if (qStr == null || qStr.isEmpty()) return qStr
        StringBuilder sb = new StringBuilder()
        buildParamMap(qStr, params).each {sb.append(it.key).append("=").append(URLEncoder.encode(it.value, 'utf-8')).append("&")}
        if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1)
        return sb.toString()
    }


    /**
     * 查询字符串 转成 map
     * @param queryStr
     * @param params
     * @return
     */
    protected Map buildParamMap(String queryStr, Map<String, Object> params) {
        if (queryStr == null || queryStr.isEmpty()) return queryStr
        // 包含 ${}字符串的情况
        def qMap = new HashMap<>()
        queryStr.split("&").each {pair ->
            def arr = pair.split("=")
            if (arr.length < 2) return
            def v = arr[1]
            if (v == null && v.isEmpty()) return
            def m = p.matcher(v)
            if (m.find()) {v = params.get(m.group("attr"))}
            if (v != null) qMap.put(arr[0], v)
        }
        return qMap
    }


    /**
     * format 字符串
     *  例:
     *      aaa${a}bbb
     *      a = 1
     *      结果 aaa1bbb
     * @param strTpl
     * @param params
     * @param limit
     * @return
     */
    protected String format(String strTpl, Map<String, Object> params, AtomicInteger limit = new AtomicInteger(0)) {
        if (limit.get() > 7) return strTpl
        def m = p.matcher(strTpl)
        if (!m.find()) return strTpl
        limit.incrementAndGet()
        return format(m.replaceAll(params.get(m.group("attr"))), params, limit)
    }
}

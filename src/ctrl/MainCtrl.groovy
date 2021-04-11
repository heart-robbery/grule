package ctrl

import cn.xnatural.app.ServerTpl
import cn.xnatural.app.Utils
import cn.xnatural.http.ApiResp
import cn.xnatural.http.Ctrl
import cn.xnatural.http.HttpContext
import cn.xnatural.http.Path
import service.FileUploader

@Ctrl
class MainCtrl extends ServerTpl {

    @Lazy def fu = bean(FileUploader)


    @Path(path = ['index.html', '/'])
    File index(HttpContext hCtx) {
        hCtx.response.cacheControl(10)
        Utils.baseDir("static/index.html")
    }

    @Path(path = 'test.html')
    File testHtml(HttpContext hCtx) {
        hCtx.response.cacheControl(3)
        Utils.baseDir("static/test.html")
    }

    @Path(path = 'favicon.ico')
    File favicon(HttpContext hCtx) {
        hCtx.response.cacheControl(3000)
        hCtx.response.contentType("image/x-icon")
        Utils.baseDir("static/favicon.ico")
    }


    @Path(path = 'health')
    ApiResp health() {
        ApiResp.ok(
            ['status': app().sysLoad <= 5 ? 'GREEN' : (app().sysLoad < 8 ? 'YELLOW' : 'RED'), 'detail':
                [
                    'db': ['status': 'UP'],
                ],
            ]
        )
    }


    @Path(path = 'file/:fName')
    File file(String fName, HttpContext hCtx) {
        if (app().profile == 'pro') {
            hCtx.response.cacheControl(1800)
        }
        fu.findFile(fName)
    }


    // ====================== api-doc =========================

    @Path(path = 'api-doc/:fName.json', produce = 'application/json')
    String swagger_data(String fName, HttpContext hCtx) {
        def f = Utils.baseDir("../conf/${fName}.json")
        if (f.exists()) {
            return f.getText('utf-8')
        }
        null
    }
    @Path(path = 'api-doc/:fName')
    File swagger_ui(String fName, HttpContext hCtx) {
        hCtx.response.cacheControl(1800)
        Utils.baseDir("static/swagger-ui/$fName")
    }


    // ==========================js =====================

    @Path(path = 'js/:fName')
    File js(String fName, HttpContext hCtx) {
        if (app().profile == 'pro') {
            hCtx.response.cacheControl(1800)
        }
        Utils.baseDir("static/js/$fName")
    }
    @Path(path = 'js/lib/:fName')
    File js_lib(String fName, HttpContext hCtx) {
        hCtx.response.cacheControl(86400) // 一天
        Utils.baseDir("static/js/lib/$fName")
    }

    @Path(path = 'components/:fName')
    File components(String fName, HttpContext hCtx) {
        if (app().profile == 'pro') {
            hCtx.response.cacheControl(1800)
        }
        if (fName.contains("OpHistory.vue")) hCtx.auth("opHistory-read")
        if (fName.contains("FieldConfig.vue")) hCtx.auth("field-read")
        if (fName.contains("DataCollectorConfig.vue")) hCtx.auth("dataCollector-read")
        // if (fName.contains("DecisionConfig.vue")) hCtx.auth("decision-read")
        if (fName.contains("Permission.vue")) hCtx.auth("grant")
        if (fName.contains("DecisionResult.vue")) hCtx.auth("decisionResult-read")
        if (fName.contains("CollectResult.vue")) hCtx.auth("collectResult-read")
        Utils.baseDir("static/components/$fName")
    }


    // =======================css ========================

    @Path(path = 'css/:fName')
    File css(String fName, HttpContext hCtx) {
        if (app().profile == 'pro') {
            hCtx.response.cacheControl(1800)
        }
        Utils.baseDir("static/css/$fName")
    }
    @Path(path = 'css/fonts/:fName')
    File css_fonts(String fName, HttpContext hCtx) {
        hCtx.response.cacheControl(86400) // 一天
        Utils.baseDir("static/css/fonts/$fName")
    }
    @Path(path = 'css/lib/:fName')
    File css_lib(String fName, HttpContext hCtx) {
        hCtx.response.cacheControl(86400) // 一天
        Utils.baseDir("static/css/lib/$fName")
    }

    // ================= 图片 ======================
    @Path(path = 'img/:fName')
    File img(String fName, HttpContext hCtx) {
        hCtx.response.cacheControl(172800)
        Utils.baseDir("static/img/$fName")
    }
}

package ctrl

import core.Remoter
import core.ServerTpl
import core.Utils
import core.http.HttpContext
import core.http.mvc.ApiResp
import core.http.mvc.Ctrl
import core.http.mvc.Path
import service.FileUploader

import java.text.SimpleDateFormat
import java.time.Duration

@Ctrl
class MainCtrl extends ServerTpl {

    @Lazy def fu = bean(FileUploader)


    @Path(path = ['index.html', '/'])
    File index(HttpContext ctx) {
        ctx.response.cacheControl(10)
        Utils.baseDir("src/static/index.html")
    }

    @Path(path = 'test.html')
    File testHtml(HttpContext ctx) {
        ctx.response.cacheControl(3)
        Utils.baseDir("src/static/test.html")
    }


    @Path(path = 'health')
    ApiResp health() {
        ApiResp.ok(
            ['status': app.sysLoad <= 5 ? 'GREEN' : (app.sysLoad < 8 ? 'YELLOW' : 'RED'), 'detail':
                [
                    'db': ['status': 'UP'],
                    'remoter': [
                        'status': {
                            def remoter = bean(Remoter)
                            if (remoter == null || remoter.lastSyncSuccess == null) return 'DOWN'
                            else {
                                def interval = System.currentTimeMillis() - remoter.lastSyncSuccess
                                if (interval < Duration.ofMinutes(5).toMillis()) return 'GREEN'
                                else if (interval < Duration.ofMinutes(20).toMillis()) return 'YELLOW'
                                else return 'RED'
                            }
                        }(),
                        'detail': {
                            def remoter = bean(Remoter)
                            def ret = [:]
                            if (remoter == null) return ret
                            ret.put('lastSyncSuccess', new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(remoter.lastSyncSuccess))
                            remoter.nodeMap.each { e ->
                                ret.put(e.key + '_total', e.value.size())
                            }
                            return ret
                        }()
                    ]
                ],
            ]
        )
    }


    @Path(path = 'file/:fName')
    File file(String fName, HttpContext ctx) {
        if (app.profile == 'pro') {
            ctx.response.cacheControl(1800)
        }
        fu.findFile(fName)
    }


    // ====================== api-doc =========================

    @Path(path = 'api-doc/:fName.json')
    String swagger_data(String fName, HttpContext ctx) {
        def f = Utils.baseDir("conf/${fName}.json")
        if (f.exists()) {
            ctx.response.contentType("application/json")
            return f.getText('utf-8')
        }
        null
    }
    @Path(path = 'api-doc/:fName')
    File swagger_ui(String fName, HttpContext ctx) {
        ctx.response.cacheControl(1800)
        Utils.baseDir("src/static/swagger-ui/$fName")
    }


    // ==========================js =====================

    @Path(path = 'js/:fName')
    File js(String fName, HttpContext ctx) {
        if (app.profile == 'pro') {
            ctx.response.cacheControl(1800)
        }
        Utils.baseDir("src/static/js/$fName")
    }
    @Path(path = 'js/lib/:fName')
    File js_lib(String fName, HttpContext ctx) {
        ctx.response.cacheControl(1800)
        Utils.baseDir("src/static/js/lib/$fName")
    }

    @Path(path = 'components/:fName')
    File components(String fName, HttpContext ctx) {
        if (app.profile == 'pro') {
            ctx.response.cacheControl(1800)
        }
        Utils.baseDir("src/static/components/$fName")
    }


    // =======================css ========================

    @Path(path = 'css/:fName')
    File css(String fName, HttpContext ctx) {
        if (app.profile == 'pro') {
            ctx.response.cacheControl(1800)
        }
        Utils.baseDir("src/static/css/$fName")
    }
    @Path(path = 'css/fonts/:fName')
    File css_fonts(String fName, HttpContext ctx) {
        ctx.response.cacheControl(1800)
        Utils.baseDir("src/static/css/fonts/$fName")
    }
    @Path(path = 'css/lib/:fName')
    File css_lib(String fName, HttpContext ctx) {
        ctx.response.cacheControl(1800)
        Utils.baseDir("src/static/css/lib/$fName")
    }
}

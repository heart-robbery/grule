package ctrl

import core.Utils
import core.module.ServerTpl
import core.module.http.HttpContext
import core.module.http.mvc.Ctrl
import core.module.http.mvc.Path
import service.FileUploader

@Ctrl
class MainCtrl2 extends ServerTpl {

    @Lazy def fu = bean(FileUploader)


    @Path(path = 'index.html')
    File index(HttpContext ctx) {
        ctx.response.cacheControl(10)
        Utils.baseDir("src/static/index.html")
    }

    @Path(path = 'test.html')
    File testHtml(HttpContext ctx) {
        ctx.response.cacheControl(3)
        Utils.baseDir("src/static/test.html")
    }


    @Path(path = 'file/:fName')
    File file(String fName, HttpContext ctx) {
        ctx.response.cacheControl(10)
        fu.findFile(fName)
    }


    @Path(path = 'api-doc/data.json')
    String swagger_data(HttpContext ctx) {
        def f = Utils.baseDir("conf/openApi.json")
        if (f.exists()) {
            ctx.response.contentType("application/json")
            return f.getText('utf-8')
        }
        null
    }
    @Path(path = 'api-doc/:fName.json')
    String swagger_data_dyn(String fName, HttpContext ctx) {
        def f = Utils.baseDir("conf/${fName}.json")
        if (f.exists()) {
            ctx.response.contentType("application/json")
            return f.getText('utf-8')
        }
        null
    }
    @Path(path = 'api-doc/:fName')
    File swagger_ui(String fName, HttpContext ctx) {
        ctx.response.cacheControl(120)
        Utils.baseDir("src/static/swagger-ui/$fName")
    }


    @Path(path = 'js/:fName')
    File js(String fName, HttpContext ctx) {
        ctx.response.cacheControl(10)
        Utils.baseDir("src/static/js/$fName")
    }
    @Path(path = 'js/lib/:fName')
    File js_lib(String fName, HttpContext ctx) {
        ctx.response.cacheControl(120)
        Utils.baseDir("src/static/js/lib/$fName")
    }


    @Path(path = 'css/:fName')
    File css(String fName, HttpContext ctx) {
        ctx.response.cacheControl(10)
        Utils.baseDir("src/static/css/$fName")
    }
    @Path(path = 'css/fonts/:fName')
    File css_fonts(String fName, HttpContext ctx) {
        ctx.response.cacheControl(120)
        Utils.baseDir("src/static/css/fonts/$fName")
    }
    @Path(path = 'css/lib/:fName')
    File css_lib(String fName, HttpContext ctx) {
        ctx.response.cacheControl(120)
        Utils.baseDir("src/static/css/lib/$fName")
    }
}

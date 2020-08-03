package ctrl

import core.Utils
import core.module.ServerTpl
import core.module.http.HttpContext
import core.module.http.mvc.ApiResp
import core.module.http.mvc.Ctrl
import core.module.http.mvc.Filter
import core.module.http.mvc.Path

import java.text.SimpleDateFormat

import static core.module.http.mvc.ApiResp.ok

@Ctrl(prefix = 'test')
class TestCtrl2 extends ServerTpl {


    @Filter
    void pre(HttpContext ctx) {
        log.info('pre ============')
    }


    @Path(path = ':fName.html')
    File html(String fName) {
        Utils.baseDir('src/static/' + fName +'.html')
    }


    @Path(path = 'cus')
    ApiResp cus(String p1) {
        log.info("here ================")
        return ok('p1: ' + p1 + ", " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()))
    }
}

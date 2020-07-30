package ctrl

import core.module.ServerTpl
import core.module.http.mvc.Path

import java.text.SimpleDateFormat

@Path(path = 'test')
class TestCtrl2 extends ServerTpl {


    @Path(path = ':fName.html')
    File html(String fName) {

    }


    @Path(path = 'cus')
    String cus(String p1) {
        return 'p1: ' + p1 + ", " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
    }
}

package ctrl

import core.module.http.mvc.Path

@Path(path = 'test')
class TestCtrl2 {


    @Path(path = ':fName.html')
    File html(String fName) {

    }


    @Path(path = 'get')
    String get(String name) {

    }
}

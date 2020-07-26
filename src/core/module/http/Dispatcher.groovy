package core.module.http

import groovy.transform.PackageScope

import java.util.concurrent.ConcurrentHashMap

@PackageScope
class Dispatcher {
    final Map<String, Object> handlers = new ConcurrentHashMap<>()


    void dispatch(HttpContext ctx) {

    }


    protected Handler findHandler(String path) {
        def arr = path.split("/")
        Map<String, Object> handlers = this.handlers
        for (String p : arr) {
            def h = match(handlers, p)
            if (h instanceof Handler) return h
            else if (h instanceof Map) handlers = h
            else if (h instanceof List) {

            }
        }
    }


    Object match(Map<String, Object> handlers, String p) {
        for (def it = handlers.iterator(); it.hasNext(); ) {
            def e = it.next()
            if (e.key == p) return e.value
        }
        return null
    }


    protected class Handler {

        handle(HttpContext ctx) {}
    }
}

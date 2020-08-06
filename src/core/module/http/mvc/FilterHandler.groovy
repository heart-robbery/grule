package core.module.http.mvc

import core.module.http.HttpContext

/**
 * Filter
 */
abstract class FilterHandler extends Handler {
    @Override
    boolean match(HttpContext ctx) { true }
}

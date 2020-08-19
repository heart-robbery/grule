package core.http.mvc

import core.http.HttpContext

/**
 * Filter
 */
abstract class FilterHandler extends Handler {


    @Override
    String type() { FilterHandler.simpleName }


    @Override
    boolean match(HttpContext ctx) { true }
}

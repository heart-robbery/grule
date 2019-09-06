package ctrl.common

import cn.xnatural.enet.common.Log
import cn.xnatural.enet.core.Environment
import cn.xnatural.enet.event.EP

import javax.annotation.PostConstruct
import javax.annotation.Resource
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.container.ContainerRequestFilter
import javax.ws.rs.container.ContainerResponseContext
import javax.ws.rs.container.ContainerResponseFilter
import javax.ws.rs.ext.Provider
import java.util.stream.Collectors

@Provider
class ResteasyMonitor implements ContainerRequestFilter, ContainerResponseFilter {
    @Resource
    Environment env;
    @Resource
    EP ep;
    Log log = Log.of(getClass());
    Set<String> ignore;


    @PostConstruct
    protected void init() {
        ignore = Arrays.stream(env.getString("ignoreTimeoutRest", "").split(","))
            .filter({s -> s != null && !s.trim().isEmpty()})
            .collect(Collectors.toSet());
    }


    @Override
    public void filter(ContainerRequestContext reqCtx) throws IOException {
        reqCtx.setProperty("startTime", System.currentTimeMillis());
    }


    @Override
    public void filter(ContainerRequestContext reqCtx, ContainerResponseContext respCtx) throws IOException {
        Object startTime = reqCtx.getProperty("startTime");
        if (startTime != null && !ignore.contains(reqCtx.getUriInfo().getPath())) {
            long spend = System.currentTimeMillis() - (long) startTime;
            if (spend > 3500) {
                log.warn("接口 '{}' 超时. spend: {}ms", reqCtx.getUriInfo().getPath(), spend);
            }
        }
    }
}

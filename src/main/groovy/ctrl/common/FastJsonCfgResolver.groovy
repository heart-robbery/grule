package ctrl.common

import cn.xnatural.enet.event.EP
import com.alibaba.fastjson.support.config.FastJsonConfig
import io.swagger.v3.oas.models.OpenAPI

import javax.annotation.PostConstruct
import javax.annotation.Resource
import javax.ws.rs.ext.ContextResolver
import javax.ws.rs.ext.Provider

import static com.alibaba.fastjson.serializer.SerializerFeature.BrowserSecure
import static com.alibaba.fastjson.serializer.SerializerFeature.WriteMapNullValue

/**
 * 用于resteasy 返回 json时, 序列化配置
 * {@link com.alibaba.fastjson.support.jaxrs.FastJsonProvider}
 */
@Provider
class FastJsonCfgResolver implements ContextResolver<FastJsonConfig> {
    @Resource
    EP ep;
    FastJsonConfig cfg;

    @PostConstruct
    protected void init() {
        cfg = new FastJsonConfig();
        cfg.setSerializerFeatures(BrowserSecure, WriteMapNullValue);
    }

    @Override
    FastJsonConfig getContext(Class<?> type) {
        if (OpenAPI.class.equals(type)) return new FastJsonConfig();
        return cfg;
    }
}

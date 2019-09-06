package module

import cn.xnatural.enet.event.EL
import cn.xnatural.enet.server.ServerTpl
import io.netty.channel.ChannelPipeline

class NettyWeb extends ServerTpl {

    NettyWeb() { super('web-netty') }


    @EL(name = 'http-netty.started')
    protected void init() {
        attrs.putAll((Map) ep.fire("env.ns", "mvc", getName()));
    }


    @EL(name = 'http-netty.addHandler')
    protected void addHandler(ChannelPipeline cp) {
        // cp.addLast()
    }
}

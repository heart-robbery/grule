import cn.xnatural.enet.common.Log
import cn.xnatural.enet.core.AppContext
import cn.xnatural.enet.event.EL
import cn.xnatural.enet.server.dao.hibernate.Hibernate
import dao.entity.Test
import groovy.transform.Field
import io.undertow.Handlers
import io.undertow.Undertow
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.server.session.*
import io.undertow.websockets.core.AbstractReceiveListener
import io.undertow.websockets.core.BufferedTextMessage
import io.undertow.websockets.core.WebSocketChannel

import java.text.SimpleDateFormat
import java.time.Duration

import dao.entity.*

//new SqlTest().run()
//return

@Field Log log = Log.of(getClass().simpleName)
@Field def ctx = new AppContext();
//ctx.addSource(new SchedServer())
ctx.addSource(new Hibernate().scanEntity(Test.class))
ctx.addSource(this)
ctx.start()


@EL(name = "sys.starting")
def sysStarting() {
    http()
}

@EL(name = "sys.started")
def sysStarted() {
    ctx.ep.fire("sched.after", Duration.ofSeconds(2), {
        println 'xxxxxxxxxxxxx'
    })

    def h = (Hibernate) ctx.ep.fire('bean.get', Hibernate.class)
    h.doWork({se ->
        def t = new Test();
        t.setName("aaaa" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()))
        t.setAge(222)
        se.saveOrUpdate(t)
        println se.createSQLQuery("select count(*) as num from test").singleResult
    })
    ctx.stop()
}


@EL(name = "sys.stopping")
def stop() {
    if (undertow) undertow.stop()
}


// undertow http
@Field def undertow;
def http() {
    undertow = Undertow.builder()
            .addHttpListener(8080, 'localhost')
            .setIoThreads(1).setWorkerThreads(1)
            .setHandler(rootHandler())
            .build()
    undertow.start()
    log.info "Start listen HTTP ${undertow.listenerInfo[0].address}"
    undertow
}


def rootHandler() {
    def h = ctrl();
    sessionHandler(h)
}


def sessionHandler(HttpHandler next) {
    def sm = new InMemorySessionManager('mem-session');
    sm.registerSessionListener(new SessionListener() {
        @Override
        void sessionCreated(Session session, HttpServerExchange seg) {
            log.info "sessionCreated. id: ${session.id}"
        }

        @Override
        void sessionDestroyed(Session session, HttpServerExchange seg, SessionListener.SessionDestroyedReason reason) {
            log.info "sessionDestroyed. id: ${session.id}"
        }
    })
    new SessionAttachmentHandler(
            {eg ->
                SessionConfig sc = eg.getAttachment(SessionConfig.ATTACHMENT_KEY);

                def se = sm.getSession(eg, sc)
                if (se == null) se = sm.createSession(eg, sc)

                if (next) next.handleRequest(eg)
            },
            sm,
            new SessionCookieConfig()
    )
}

// 接口控制层
def ctrl() {
    Handlers.path()
        .addExactPath("/", {eg -> eg.responseSender.send("index")})
        .addExactPath("/test", {eg -> eg.responseSender.send("xxxxxxxxxxxxx")})
        .addExactPath("/ws", Handlers.websocket({eg, ch ->
            ch.getReceiveSetter().set(new AbstractReceiveListener() {
                @Override
                protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage msg) throws IOException {
                    println(msg.getData())
                }
            })
            ch.resumeReceives()
        }))
        .addExactPath("/shutdown", Handlers.ipAccessControl({eg -> ctx.stop()}, false).addAllow("127.0.0.1"))
}
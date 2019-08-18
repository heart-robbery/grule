import cn.xnatural.enet.common.Log
import cn.xnatural.enet.core.AppContext
import cn.xnatural.enet.event.EL
import cn.xnatural.enet.server.sched.SchedServer
import com.alibaba.druid.pool.DruidDataSourceFactory
import groovy.sql.Sql
import groovy.transform.Field
import io.undertow.Handlers
import io.undertow.Undertow
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.resource.ClassPathResourceManager
import io.undertow.server.session.*
import io.undertow.websockets.core.AbstractReceiveListener
import io.undertow.websockets.core.BufferedTextMessage
import io.undertow.websockets.core.WebSocketChannel
import io.undertow.websockets.core.WebSockets

import javax.annotation.Resource
import java.time.Duration

//new SqlTest().run()
//return

@Field Log log = Log.of(getClass().simpleName)
@Field def ctx = new AppContext();
@Resource @Field Sql sql


// 系统功能添加区
ctx.addSource(new SchedServer())
//ctx.addSource(new Remoter())
ctx.addSource(this)
ctx.start()


@EL(name = "env.configured", async = false)
def envConfigured() {
    // ctx.addSource(new Sql(DruidDataSourceFactory.createDataSource(ctx.env().group("ds"))))
}

@EL(name = "sys.starting")
def sysStarting() {
    ctx.addSource(new Sql(DruidDataSourceFactory.createDataSource(ctx.env().group("ds"))))
    web()
}

@EL(name = "sys.started")
def sysStarted() {
    try {
        // logic()
    } finally {
        // ctx.stop()
    }
}


@EL(name = "sys.stopping")
def stop() {
    if (undertow) undertow.stop()
    if (sql) sql.close()
}


def logic() {
    ctx.ep.fire("sched.after", Duration.ofSeconds(2), {
        println 'xxxxxxxxxxxxx'
    })

    sql.execute('''
      create table if not exists test (
          name varchar(20),
          age int(10)
      )
    ''')
    sql.executeInsert("insert into test values(?, ?)", ["xxxx" + System.currentTimeMillis(), 1111])
    println sql.firstRow("select count(*) as num from test").num
}


// undertow web
@Field def undertow
def web() {
    undertow = Undertow.builder()
            .addHttpListener(8080, 'localhost')
            .setIoThreads(1).setWorkerThreads(1)
//            .setHandler(ctrl())
             .setHandler(Handlers.trace(ctrl()))
            // .setHandler(memSession(ctrl()))
            .build()
    undertow.start()
    log.info "Start listen HTTP ${undertow.listenerInfo[0].address}"
    undertow
}


def memSession(HttpHandler next) {
    def sm = new InMemorySessionManager('mem-session');
    sm.registerSessionListener(new SessionListener() {
        @Override
        void sessionCreated(Session se, HttpServerExchange seg) {
            log.info "HTTP session created. id: ${se.id}"
        }

        @Override
        void sessionDestroyed(Session se, HttpServerExchange seg, SessionListener.SessionDestroyedReason reason) {
            log.info "HTTP session destroyed. id: ${se.id}"
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
    // webSocket 处理器
    def ws = Handlers.websocket({eg, ch ->
        log.info "New WebSocket Connection '${ch.peerAddress}', current total: ${eg.peerConnections.size()}"
        ch.getReceiveSetter().set(new AbstractReceiveListener() {
            @Override
            protected void onFullTextMessage(WebSocketChannel wsc, BufferedTextMessage msg) throws IOException {
                log.info "receive websocket client '${wsc.peerAddress}' msg: ${msg.data}"
            }
        })
        ch.resumeReceives()
    })
    ctx.ep.fire("sched.cron", "0/17 * * * * ?", {ws.peerConnections.each {conn -> WebSockets.sendText("qqqq" + new Date(), conn, null)}})

    Handlers.path()
        .addPrefixPath("/", Handlers.resource(new ClassPathResourceManager(getClass().classLoader, "static")))
        .addExactPath("/test", {eg -> eg.responseSender.send("xxxxxxxxxxxxx")})
        .addExactPath("/ws", ws)
        .addExactPath("/xxx", {eg -> eg.responseSender.send("22222222222222")})
        // .addExactPath("/upload", {eg -> })
        .addExactPath("/shutdown", Handlers.ipAccessControl({eg -> ctx.stop()}, false).addAllow("127.0.0.1"))
}
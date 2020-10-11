# DSL 规则执行框架
demo http://39.104.28.131:9090/index.html test:test

![Image text](https://gitee.com/xnat/tmp/raw/master/img/decision.png)
![Image text](https://gitee.com/xnat/tmp/raw/master/img/dataCollector.png)


# gy

#### 介绍
groovy 快速开发代码
1. 小功能代码模板
2. web微服务

#### 软件架构
入口 src/main.groovy

前端文件 src/static

#### 配置:
    配置一般包含两个文件:
        1. 默认加载 系统属性 和 配置文件conf/app.conf
        2. 通过启动参数 -Dprofile=dev 指定 加载conf/app-dev.conf 配置文件,不配置则不加载
        3. 优先级: 系统属性 > app-[profile].conf > app.conf


#### Remoter 集群分布式
```
remoter {
    // 集群的服务中心地址. 格式为: host1:port1,host2:port2. 域名可配置多个Ip
    masterHps='xnatural.cn:8001'
    // 一般被选为master的应用设置为true, 以保证master之间信息同步
    // master=true
}
```
    同步调用: fire(应用名, 事件名, 参数...)
    异步调用: fireAsync(应用名, 事件名, 回调函数, 参数...)
    例: bean(Remoter).fire('gy', 'eName1', ['p1'])
    
#### http server
    @Ctrl: 标明类是个Ctroller层类
    
    @Path: 标明是个路径匹配的处理器
        例:
        @Path(path = 'js/:fName')
        File js(String fName, HttpContext ctx) {
            ctx.response.cacheControl(10)
            Utils.baseDir("src/static/js/$fName")
        }
    
    @Filter: 标明是个filter
        例:
        @Filter(order = 1)
        void filter1(HttpContext ctx) {
            log.info('filter1 ============')
        }

    @WS: 标明是个websocket处理器
        例:
        @WS(path = 'msg')
        void wsMsg(WebSocket ws) {
            log.info('WS connect. {}', ws.session.sc.remoteAddress)
            ws.listen(new Listener() {
                @Override
                void onClose(WebSocket wst) {
                    wss.remove(wst)
                }
                @Override
                void onText(String msg) {
                    log.info('test ws receive client msg: {}', msg)
                }
            })
        }

#### OkHttpSrv http客户端api封装
    get请求:  get("localhost:8080/test/cus?p1=111").param("p2", "222").execute()
    表单请求: post("localhost:8080/test/cus?p1=111").param("p2", "222").execute()
    json请求: post("localhost:8080/test/cus").jsonBody("json string").execute()
    文件上传: post("localhost:8080/test/cus?p1=111").param("file", new File("/tmp/a.txt")).execute()
    WebSocket: ws("ws://localhost:7100/test/ws", 60, {msg, ws -> println msg}); 60秒重试一次(如果连接失败) 
    集群应用接口: get("http://应用名/test/cus").execute(). 此功能依赖Remoter


#### 安装教程

jdk8, gradle6.5+

#### 使用说明

##### 1. git clone https://gitee.com/xnat/gy.git
##### 2. 
    linux: nohup sh start.sh [-Dprofile=pro] > /dev/null 2>&1 &
    windows: ./start [-Dprofile=dev]


#### 参与贡献
xnatural@msn.cn
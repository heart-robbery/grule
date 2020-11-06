### 介绍
groovy 快速开发代码, 由groovy实现的迷你类SpringBoot模板项目
##### 框架应用例: [Groovy DSL 规则引擎](https://gitee.com/xnat/gy/tree/rule)
1. 小功能代码模板
2. web微服务

### 软件架构
* src/main.groovy: 入口类
* bin: 对应groovy安装包bin目录
* conf: 对应groovy安装包conf目录, 和项目配置文件存放目录
* src/core: 核心功能: AppContext, HttpServer, AioServer等等
* src/ctr: mvc Controller层
* src/static: 前端文件

### 使用说明
jdk8, gradle6.5+

1. git clone https://gitee.com/xnat/gy.git
2. 
 * linux: nohup sh start.sh -Xmx512m -Xms128m [-Dprofile=pro] > /dev/null 2>&1 &
 * windows: ./start [-Dprofile=dev]

### 配置 [例](https://gitee.com/xnat/gy/blob/master/conf/app.conf)
1. 默认加载 系统属性 和 配置文件conf/app.conf
2. 通过启动参数 -Dprofile=dev 指定 加载conf/app-dev.conf 配置文件,不配置则不加载
3. 优先级: 系统属性 > app-[profile].conf > app.conf
4. 日志默认输出项目目录log下, -Dlog.path=修改日志存放目录, -Dlog.file.name=日志文件名
5. 日志实现配置: logback.groovy
6. 修改日志等级: -Dlog.level.日志名=debug

### 快速微服务
* 应用由 一个执行上下文AppContext 和 多个ServerTpl组成
* 应用由 def app = new AppContext(); app.start() 开始 [例](https://gitee.com/xnat/gy/blob/master/src/main.groovy)
* 启动阶段:
    - 初始化环境(系统配置): 事件: sys.inited
    - 初始各组件服务: 事件: sys.starting
    - 启动完成: 事件: sys.started
##### 抽象应用由四部分组成 [AppContext](https://gitee.com/xnat/gy/blob/rule/src/core/AppContext.groovy)
1. 应用环境属性(系统所有配置). 加载app.conf,系统属性, app-[profile].conf
2. 任务执行器(公用线程池). 系统所有任务由这一个线程池执行
3. bean容器. 添加公用bean: app.addSource(new OkHttpSrv())
4. 事件中心 <https://gitee.com/xnat/enet>
##### 基本抽象服务模板 ServerTpl
* 获取属性: getInteger('属性名', 默认值), getBoolean('属性名', 默认值) ... 对应配置 当前SeverTpl服务名.属性名
* 异步任务: async {任务}
* 事件触发: ep.fire('event1', 'param1') 参见: <https://gitee.com/xnat/enet>
* 队列执行: queue('xx') {排队任务}. 加入到名为xx的队列排队执行
* 暴露bean给应用: exposeBean(bean实例) 
* bean容器 和AppContext一样都提供bean容器功能. 获取实例bean: bean(OkHttpSrv)

###特色1: [Remoter](https://gitee.com/xnat/gy/blob/master/src/core/Remoter.groovy) 集群分布式
Remoter是多应用之间的连接器,简化跨系统调用,由 [AIO](https://gitee.com/xnat/gy/blob/master/src/core/aio/AioServer.groovy) 实现
```
// 暴露集群通信端口
aioServer.hp=':9001'
// 应用集群配置
remoter {
    // 加入到集群. 集群的服务中心地址. 格式为: host1:port1,host2:port2. 域名可配置多个Ip
    masterHps='xnatural.cn:8001'
    // 一般被选为master的应用设置为true, 以保证master之间信息同步
    // master=true
}
```
##### 远程调用 
* 同步调用: def result = bean(Remoter).fire(应用名, 事件名, 参数...)
    - 例: bean(Remoter).fire('gy', 'eName1', ['p1'])
* 异步调用: bean(Remoter).fireAsync(应用名, 事件名, 回调函数, 参数...)
    - 例: [远程调用](https://gitee.com/xnat/gy/blob/master/src/service/TestService.groovy#L178)

    
###特色2: 自实现 [HttpServer](https://gitee.com/xnat/gy/blob/master/src/core/http/HttpServer.groovy) [例](https://gitee.com/xnat/gy/blob/master/src/ctrl/TestCtrl.groovy)
    @Ctrl: 标明类是个Controller层类
    
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


### 参与贡献
xnatural@msn.cn
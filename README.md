### 介绍
groovy 快速开发代码, 由groovy实现的迷你类SpringBoot模板项目. 基于[APP](https://gitee.com/xnat/app)
##### 框架应用例: [Groovy DSL 规则引擎](https://gitee.com/xnat/gy/tree/rule)
1. 小功能代码模板
2. web微服务

### 软件架构
* src/main.groovy: 入口类
* bin: 对应groovy安装包bin目录
* conf: 对应groovy安装包conf目录, 和项目配置文件存放目录
* src/core: 核心功能: HttpSrv 等等
* src/ctr: mvc Controller层
* src/static: 前端文件

### 使用说明
jdk8, gradle6.5+

1. git clone https://gitee.com/xnat/gy.git
2. 
 * linux: nohup sh start.sh -Xmx512m -Xms128m [-Dprofile=pro] > /dev/null 2>&1 &
 * windows: ./start [-Dprofile=dev]

### 特色1: [Remoter](https://gitee.com/xnat/remoter) 集群分布式
Remoter是多应用之间的连接器,简化跨系统调用
```
// 应用集群配置
remoter {
    // 暴露给集群之间通信端口
    hp=':9001'
    // master集群服务中心
    masterHps='xnatural.cn:8001'
    // 是否为master
    // master=true
}
```
##### [远程调用](https://gitee.com/xnat/remoter)
* 同步调用: def result = bean(Remoter).fire(应用名, 事件名, 参数...)
    - 例: ```bean(Remoter).fire('gy', 'eName1', ['p1'])```
* 异步调用: bean(Remoter).fireAsync(应用名, 事件名, 回调函数, 参数...)
    - [例](https://gitee.com/xnat/gy/blob/master/src/service/TestService.groovy#L178)
    ```
        bean(Remoter).fire('gy', 'eName1', {result -> // 回调函数
        }, ['p1'])
    ```

    
### 特色2: 自实现 [http服务](https://gitee.com/xnat/http)
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
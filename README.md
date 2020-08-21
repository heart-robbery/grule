# gy

#### 介绍
groovy 快速开发代码
1. 小功能代码模板
2. web微服务

#### 软件架构
入口 src/main.groovy

前端文件 src/static

###### 集常用功能: 
    ratpackweb, sched(quartz), cache(ehcache), 
    redis, jpa(hibernate), OkHttpClient, Remoter(多应用tcp通信)


#### 配置:
    共用属性: baseDir(当前项目目录), System.properties
    app.conf: 可像 $baseDir 这样使用共用属性
    app-[profile].conf: 可使用共用属性和app.conf中的属性
    优先级: 系统属性 > app-[profile].conf > app.conf
    

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
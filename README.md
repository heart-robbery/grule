# gy

#### 介绍
groovy 快速开发代码
1. 小功能代码模板
2. web微服务

#### 软件架构
入口 src/main.groovy

前端文件 src/static

ratpackweb, sched(quartz), cache(ehcache), 
redis, jpa(hibernate), OkHttpClient, Remoter(多应用tcp通信)


#### 配置:
    共用属性: baseDir(当前项目目录), System.properties
    app.conf: 可像 $baseDir 这样使用共用属性
    app-[profile].conf: 可使用共用属性和app.conf中的属性
    优先级: 系统属性 > app-profile.conf > app.conf
    
#### Remoter 集群分布式
    集群中有一台或多台被选为master
    master收集集群中的注册到自己应用信息, 并把同步这些信息给每个注册到自己的服务
    应用信息: name, id, tcp(host:port), http等等
    应用节点通过选择自己的master来加入集群, 并从master接取注册到这台master上的各种应用服务
    通过配置 remoter.master:'应用名字', remoter.masterHps: 'host:port,host1:port1'指定master
    master的注册功能和发布通知功能由TCPServer实现
    加入集群和集群保存活动的功能由TCPClient实现
    

#### 安装教程

jdk8, gradle5+

#### 使用说明

##### 1. git clone https://gitee.com/xnat/gy.git
##### 2. 
    linux: nohup sh start.sh [-Dprofile=pro] > /dev/null 2>&1 &
    windows: ./start [-Dprofile=dev]


#### 参与贡献
xnatural@msn.cn
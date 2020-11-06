### Groovy DSL 规则执行框架
例子 http://xnatural.cn:9090/ test:test

#### 介绍
以 [GY](https://gitee.com/xnat/gy) 框架为基础 DSL 规则执行引擎

![Image text](https://gitee.com/xnat/tmp/raw/master/img/decision_dashbrod.png)
![Image text](https://gitee.com/xnat/tmp/raw/master/img/decisions.png)
![Image text](https://gitee.com/xnat/tmp/raw/master/img/decision_records.png)
![Image text](https://gitee.com/xnat/tmp/raw/master/img/decision_result_detail.png)
![Image text](https://gitee.com/xnat/tmp/raw/master/img/collect_records.png)


### 使用说明
jdk8, gradle6.5+

1. git clone https://gitee.com/xnat/gy.git
2. git checkout rule
 * linux: nohup sh start.sh -Xmx512m -Xms512m -Dprofile=rule > /dev/null 2>&1 &
 * windows: ./start -Dprofile=rule


#### 参与贡献
xnatural@msn.cn
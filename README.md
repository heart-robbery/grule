### Groovy DSL 规则执行框架
例子 http://xnatural.cn:9090/ test:test

#### 决策规则定义
```
// 决策id: 必须唯一
决策id = 'test1'
决策名 = '测试1'
决策描述 = ''

// 返回的调用决策方的结果属性值
// 返回属性 '身份证号码'

策略定义 {
    策略名 = 'P_预处理'

    // 执行此策略的条件, false: 不执行, true 或者 不配置默认 执行此策略
    // 条件 { true }

    规则定义 {
        规则名 = 'R_参数验证'
        // 属性定义 '处置代码', 'DC_INPUT_01'

        拒绝 {
            !身份证号码 || !手机号码 || !姓名
        }
    }
}
```
#### 决策定义
![Image text](https://gitee.com/xnat/tmp/raw/master/img/decisions.png)

#### 图表展示 
![Image text](https://gitee.com/xnat/tmp/raw/master/img/decision_dashbrod.png)

#### 决策执行记录
![Image text](https://gitee.com/xnat/tmp/raw/master/img/decision_records.png)

#### 决策执行详情
![Image text](https://gitee.com/xnat/tmp/raw/master/img/decision_result_detail.png)

#### 决策执行过程中生产的数据收集
![Image text](https://gitee.com/xnat/tmp/raw/master/img/collect_records.png)


### 使用说明
jdk8, gradle6.5+

1. git clone https://gitee.com/xnat/rule.git
2. 
 * linux: nohup sh start.sh -Xmx512m -Xms512m > /dev/null 2>&1 &
 * windows: ./start


#### 参与贡献
xnatural@msn.cn
### Groovy DSL 动态规则(rule)执行引擎
持续更新中...

DSL: 开发 和 业务 共识的语言. 方便业务表达需求, 方便开发理解业务

一般程序逻辑可以抽象成为: 按一定规则处理数据,然后返回数据

此框架可以执行成千上万的一般程序逻辑, 可以动太修改逻辑, 可以动态修改数据

可用于账务规则, 风控规则, 电商价格规则, 测试规则, 各种业务规则

* 引擎抽象一般程序为一条决策,一条决策包含多个规则
* 引擎用自创DSL规则语言表达规则
```
    规则定义 {
        规则名 = '年龄限制'
        拒绝 { 年龄 > 40 || 年龄 < 25 }
    }
```
* 引擎抽象数据为指标(字段)
* 指标值来源于数据源配置包含: 接口,SQL,脚本 3种类型

例子 http://xnatural.cn:9090/ test:test

### 各种规则
```
规则定义 {
    规则名 = '大小比较'
    拒绝 { 年龄 > 40 || 年龄 < 25 }
}
```
```
规则定义 {
    规则名 = '通过规则'
    通过 { 年龄 == 30 }
}
```
```
规则定义 {
    规则名 = '赋值规则'
    操作 { 产品代码 = 'xx' }
}
```
```
规则定义 {
    规则名 = '条件赋值规则'
    操作 {
        if (当前时间 > '2021-03-02 00:00:00') {
            单价 = 10
        } 
    }
}
```
```
规则定义 {
    规则名 = '列表判断规则'

    禁入省 = ["台湾", "香港"]

    拒绝 { 工作省 in 禁入省}
}
```
```
规则定义 {
    规则名 = '包含规则'
    拒绝 { "$姓名".contains("xx") }
}
```

### 决策规则定义
```
决策id = 'test1'
决策名 = '测试1'
决策描述 = ''

// 返回的调用决策方的结果属性值
// 返回属性 '身份证号码'

// 一组规则
策略定义 {
    策略名 = 'P_预处理'

    // 执行此策略的条件, false: 不执行, true 或者 不配置默认 执行此策略
    // 条件 { true }

    规则定义 {
        规则名 = 'R_参数验证'

        拒绝 {
            !身份证号码 || !手机号码 || !姓名
        }
    }

    规则定义 {
        规则名 = '年龄限制'

        拒绝 { 年龄 > 40 || 年龄 < 25 }
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


### 参与贡献
xnatural@msn.cn
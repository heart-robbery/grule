# Groovy DSL 动态规则(rule)执行引擎
持续更新中...

DSL: 开发 和 业务 共识的语言. 方便业务表达需求, 方便开发理解业务

一般程序逻辑(此框架)可以抽象成为: 按一定规则处理数据,然后返回数据

例: 一个人可以用成百上千个属性组成,由这些属性衍生出新的属性(例如,好人/坏人) 返回一个业务结果(0..多个属性值)

例: 一般接口: 查询数据库或接口,处理简单逻辑,最后返回一些属性

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

# 规则定义
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
# 策略定义
* 多个规则组成
* 策略执行规则前, 会按顺序执行操作,条件函数
* 条件函数返回false, 则跳出, 继续执行下一个策略
```
策略定义 {
    策略名 = '004244'
    
    操作 { jj_代码 = '004244' }
    条件 { jj_名 }
    
    规则定义 {
      拒绝 { true }
    }
}
```

# 决策定义
* 由一个或多个策略组成
* 触发当前决策: http://ip:port/decision?decisionId=test1
```
决策id = 'test1'
决策名 = '测试1'
决策描述 = ''

// 返回的调用决策方的结果属性值
// 返回属性 '身份证号码'

策略定义 {
    策略名 = 'P_预处理'

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
# 功能预览图
## 决策定义
![Image text](https://gitee.com/xnat/tmp/raw/master/img/decisions.png)

## 决策执行统计 
![Image text](https://gitee.com/xnat/tmp/raw/master/img/decision_dashbrod.png)

## 决策规则统计 
![Image text](https://gitee.com/xnat/tmp/raw/master/img/countRule.png)

## 决策执行记录
![Image text](https://gitee.com/xnat/tmp/raw/master/img/decision_records.png)

## 决策执行详情
![Image text](https://gitee.com/xnat/tmp/raw/master/img/decision_result_detail.png)

## 决策执行过程中生产的数据收集
![Image text](https://gitee.com/xnat/tmp/raw/master/img/collect_records.png)

# 权限
静态权限: 权限管理, 用户管理, 用户登陆, 新增用户, 删除用户, 决策创建, 查看字段, 新增字段, 更新字段, 删除字段, 查看收集器, 新增收集器, 更新收集器, 删除收集器, 查看操作历史, 查看决策结果, 查看收集记录

动态资源(决策)权限: 每添加一个决策 就会生成3个与之对应的动态权限(读, 删, 改)
## 用户分类
* 超级用户(系统管理员): 拥有 权限管理 的用户
* 用户管理员(组管理员: 管理组相同的用户): 拥有 用户管理 的用户
* 普通用户: 非 权限管理 和 用户管理 的用户

![Image text](https://gitee.com/xnat/tmp/raw/master/img/userlist.png)

## 权限作用域
* 超级用户: 可以 修改任何 用户的任何权限
* 用户管理员: 
    * 可以 修改同组 用户的 可分配(自身拥有的)的权限
    * 可以分配同组用户创建的动态权限
* 普通用户: 可以 操作 拥的每个权限对的 操作

![Image text](https://gitee.com/xnat/tmp/raw/master/img/userchange.png)

# 使用说明
jdk8, gradle6.5+

1. IntelliJ IDEA 运行 main.groovy
2. 
 * linux: nohup sh start.sh -Xmx512m -Xms512m > /dev/null 2>&1 &
 * windows: ./start


# 参与贡献
xnatural@msn.cn
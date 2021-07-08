# Groovy DSL 动态规则(rule)执行引擎

> DSL(特定领域语言): 开发 和 业务 共识的语言. 方便业务表达需求, 方便开发理解业务

> 一个业务需求(一般程序,一个接口)可以抽象成为: 按一定业务逻辑规则处理数据,然后返回数据
> > + 一个人可以用成百上千个属性组成,由这些属性衍生出新的属性(例如,好人/坏人) 返回一个业务结果(0..多个属性值)
> > + 一般接口: 查询数据库或接口,对数据进行简单逻辑计算,最后返回一些属性

> 此框架可以执行成千上万的一般程序逻辑, 可以动态修改逻辑, 可以动态修改数据

> 可用于风控规则, 电商价格规则, 其它业务规则

# 框架概念
* 框架抽象一个业务需求(一般程序,一个接口)为一条决策,一条决策包含多个业务规则
* 框架用自创DSL策略规则语言表达业务逻辑
```
    规则 {
        规则名 = '芝麻分限制'
        拒绝 { 芝麻分 < 620 }
    }
```
* 框架抽象数据为指标(字段)
* 指标值来源于数据源配置(收集器)包含: 接口,SQL,脚本3种类型

> 例子 http://xnatural.cn:9090/ test:test

# 规则定义
```
规则 {
    规则名 = '大小比较'
    拒绝 { 年龄 > 40 || 年龄 < 25 }
}
```
```
规则 {
    规则名 = '通过规则'
    通过 { 年龄 == 30 }
}
```
```
规则 {
    规则名 = '赋值规则'
    操作 { 产品代码 = 'xx' }
}
```
```
规则 { // 电商价格规则
    规则名 = '条件赋值规则'
    操作 {
        if (当前时间 > '2021-03-02 00:00:00') {
            单价 = 10
        } 
    }
}
```
```
规则 {
    规则名 = '列表判断规则'

    禁入省 = ["台湾", "香港"]

    拒绝 { 工作省 in 禁入省 }
}
```
```
规则 {
    规则名 = '包含规则'
    拒绝 { "$姓名".contains("xx") }
}
```

# 策略定义
* 多个规则,评分卡,子决策组成, 按顺序依次执行
* 策略执行规则前, 会按顺序执行操作,条件函数
* 条件函数返回false, 则跳出, 继续执行下一个策略
## 规则策略
```
策略 {
    策略名 = '004244'
    
    操作 { jj_代码 = '004244' }
    条件 { jj_名 }
    
    规则 {
      拒绝 { true }
    }
}
```
## 评分卡策略
* 模型是由多个条目组成
* 每个条目由 属性名, 值匹配范围, 分值 组成
* 模型分计算: 依次遍历每个条目,得到匹配的分值相加
* 最终评分 = 基础分 + 模型计算得分
```
策略 {
    策略名 = '测试评分卡'
    
    评分卡 {
       评分卡名 = "测试评分卡"
       基础分 = 600
       模型 = [
           ['年龄', 18..24, 40],
           ['年龄', 25..45, 80],
           ['年龄', 46..60, 50],
           ['性别', 'F', 50],
           ['性别', 'M', 60],
           [{ -> 100}], // 函数分
           ['芝麻分'], //变量值分
           ['逾期次数', {次数 -> -((次数?:0) * 100)}], // 动态分
           ['工作成市', ['成都', '巴中'], 99]
       ]
       赋值变量 = '评分结果'
    }
    
    规则 {
      规则名 = "评分判断"
      拒绝 { 评分结果 < 60 }
    }
}
```
## 子决策策略
* 嵌套其它决策执行. 不会形成一条单独的决策执行记录
* 当子决策执行结果为Reject的时,会结束当前决策执行
* 子决策的返回属性 会设置 到当前执行上下文,可以使用
```
策略 {
    策略名 = '测试子决策'
    
    决策 {
       决策id = "test2"
    }
}
```

# 决策定义
* 由一个或多个策略组成
* 触发当前决策: http://ip:port/decision?decisionId=test1
* 接口返回: 3种结果(Accept, Reject, Review) 和多个返回属性值(由配置指定)
```json
{
  "decideId": "647f297a2e4540dfa93991b5b6e7b44d",
  "result": "Accept",
  "decisionId": "jj_analyse",
  "status": "0000",
  "desc": null,
  "data": {
    "jj_code": "165525"
   }
}
```
* 依次从上往下执行
```
决策id = 'test1'
决策名 = '测试1'
决策描述 = ''

// 返回的调用决策方的结果属性值
// 返回属性 '身份证号码'

// 预操作执行
操作 {}

策略 {
    策略名 = 'P_预处理'
    // 条件 { true }

    规则 {
        规则名 = 'R_参数验证'
        拒绝 {
            !身份证号码 || !手机号码 || !姓名
        }
    }

    规则 {
        规则名 = '年龄限制'
        拒绝 { 年龄 > 40 || 年龄 < 25 }
    }
}
```
## 自定义决策函数
```
决策id = 'test1'
决策名 = '测试1'

策略 {
    策略名 = '测试策略'
    规则 {
        规则名 = '使用函数'
        操作 { 钉钉消息("发个消息") }
    }
}

函数定义("钉钉消息") {String msg -> 
    Utils.http()
        .post("https://oapi.dingtalk.com/robot/send?access_token=7e9d8d97e6b5e76a6a07b0c5d7c31e82f0fbdb8ced1ac23168f9fd5c28c57f1a")
        .jsonBody(JSON.toJSONString(
            [msgtype: 'text', text: [content: "Fund: " + msg], at: ['isAtAll': false]]
        )).debug().execute()
}
```

![Image text](https://gitee.com/xnat/tmp/raw/master/img/decisions.png)


# 指标/字段/属性
## 使用 '年龄' 指标
```
拒绝 { 年龄 > 50 }
```

## 指标值获取
<!-- 
@startuml
skinparam ConditionEndStyle hline
:指标名;
if (执行上下文) then (已存在值)
  if (值来源是否为收集器) then (是)
    :收集器;
  else (否)
  :返回值;
  detach
  endif
else (不存在值)
  if (入参) then (存在值)
    :保存指标值到 __执行上下文__;
    :返回值;
    detach
  else (不存在)
    :收集器;
  endif
endif
->//计算//;
:指标值;
:保存指标值到 __执行上下文__;
:返回值;
@enduml
-->
![Image text](http://www.plantuml.com/plantuml/png/SoWkIImgAStDuIhEpimhI2nAp5LmpizBoIp9pCzppKi9BgdCILN8oCdCI-MoUjRJ_cn1-zC9lTPScMaA6iywbxzOsFDaHzUJ7TtFfhLhAfHafEOfQ3pTlkdfsXbFvwnush17aqj10QGKo7msT-cpNHEUpLZ_TCAo9pjsFPkoxUNijgSpLy2q0MM0ge702Yvb3UIdvXIdAcW0zO0ahLxid_9qzZoWQI2fbDIInEGCa1gWUzEu82gVxEZ5jBrrwTF-9fX5S6ceTK_spmKAGVm657tQiK4XFXxDR_7n80lH7O3a13JBCNm2ToM4rBNJrt-nRk7pTTFrzQrX0GisbMZd83l50MWgC0u1)

## 指标列表
![Image text](https://gitee.com/xnat/tmp/raw/master/img/rule_fields.png)

# 收集器(数据源)
> 支持从SQL(数据库),http(接口),script(自定义groovy脚本函数)获取数据
## 收集器执行
<!--
@startuml
skinparam ConditionEndStyle hline
:指标名;
-> 选择;
:收集器;
-> 根据上下文计算;
:数据key;
note left
  用于判断是否需要重复计算
end note
if (执行上下文) then(存在key)
  :收集结果;
else (不存在)
  :执行收集器;
  :保存收集结果到 __执行上下文__;
  note right
    收集结果可能是多个指标的值映射
    也可能是单个指标值
  end note
endif

split
 :多值映射;
  note left
    收集结果是个Map
  end note
  -> get;
  if (指标值) then(是个函数)
      :值函数计算;
  endif
split again
 :单值;
end split

:指标值;
:返回值;
@enduml
-->
![Image text](http://www.plantuml.com/plantuml/png/PLBDpX8n5DttARhaM_W2Cudv4kFIbGymcOG2pJSKmjG5Eup40M6KH404H4W8eWa_ApEeX9UPTcRUmjkMZAXTDQVdd7lklRttkTlWBweUyXyegxiDjugVr5YHSbfZJrdnEMzw15SyoWYoP3-Goq0CGXizUeopLbVslje03xzdizVYurR3SdcIuJwEtiHHJuw3TBzAzXyKQtG4_84qRSHgd62Fb3Z2E1bkunzlHMSjnpivEOZ19fktqitBB0Z5EZHgH5WHAn6Y9LoGtI_fgfyNkCEyGbX1x2PYlWNxEp2zHaf-lfUBkOs8vnDSYAFGa3J3kDn41oo-V0B6hLPqZjXn_gdeE8gjcsZGSWMwWFENwjqXVNLMtQodSVJDZ2sPjaNhbvminR6j5V7fynzYECg9m8Btl6Muq192VjsZKCa2ozmcZm6p_3y5s8BdCxT-wuOnhAXCk9AgOUObhsCq8X6SOLqm9tqiU3Q8MOVIwbGc57RBBcKgMZW2fgstPUBcNqR1LdePYjb2t--10v_kDm00)

## 决策执行过程中生产的数据收集
![Image text](https://gitee.com/xnat/tmp/raw/master/img/collect_records.png)

# 决策执行
## 决策执行记录
![Image text](https://gitee.com/xnat/tmp/raw/master/img/decision_records.png)

## 决策执行详情
![Image text](https://gitee.com/xnat/tmp/raw/master/img/decision_result_detail.png)

# 统计图
## 决策执行统计 
![Image text](https://gitee.com/xnat/tmp/raw/master/img/decision_dashbrod.png)

## 决策规则统计 
![Image text](https://gitee.com/xnat/tmp/raw/master/img/countRule.png)


# 权限
> 静态权限: 权限管理, 用户管理, 用户登陆, 新增用户, 删除用户, 决策创建, 查看字段, 新增字段, 更新字段, 删除字段, 查看收集器, 新增收集器, 更新收集器, 删除收集器, 查看操作历史, 查看决策结果, 查看收集记录

> 动态资源(决策)权限: 每添加一个决策 就会生成3个与之对应的动态权限(读, 删, 改)

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
jdk8, gradle6.5+, mysql5.7+/MariaDB10.2+

> 系统使用groovy应用开发模板: [GY](https://gitee.com/xnat/gy)

1. IntelliJ IDEA 运行 main.groovy
2. 
 * linux: nohup sh start.sh -Xmx512m -Xms512m > /dev/null 2>&1 &
 * windows: ./start

# TODO
* redis 收集器
* ~~password 加密~~
* 用户权限关联独为单独一个实体
* 拆FieldManager-可用trait

# 参与贡献
xnatural@msn.cn
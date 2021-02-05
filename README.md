### Groovy DSL 规则执行框架
例子 http://xnatural.cn:9090/ test:test

#### 决策规则定义
```
// 决策id: 必须唯一
决策id = 'jj_analyse'
决策名 = 'jj分析'

返回属性 'jj_代码'
返回属性 'jj_名'

策略定义 {
    策略名 = '预处理'
    
    规则定义 {
        规则名 = '参数验证'
        拒绝 { jj_名 == null }
    }
    
    规则定义 {
        规则名 = '非债券'
        拒绝 { "$jj_名".contains("债") }
    }
    
    规则定义 {
        规则名 = '非ETF'
        拒绝 { "$jj_名".contains("ETF") }
    }

    规则定义 {
        规则名 = '只看净值不大的jj'
        拒绝 { jj_最新净值 > 2.5 }
    }
}
策略定义 {
    策略名 = '净值分析'

    规则定义 {
        规则名 = '历史最低值'
        拒绝 { jj_历史最低价 == null }

        操作 {
            if ((jj_最新净值 <= jj_历史最低价 || (jj_最新净值 - jj_历史最低价) < 0.005) && (jj_历史最高价_近6月 - jj_最新净值 > 0.3)) {
                历史最低 = true
                钉钉消息("历史最低: $jj_code : $jj_名")
            }
        }
    }
    
    规则定义 {
        规则名 = '新jj判断'
        拒绝 { jj_历史条数 == null }

        操作 {
            if (jj_历史条数 < 30) { //只有几个月数据
                新jj = true
                钉钉消息("新jj: $jj_code : $jj_名")
            }
        }
    }
    
    规则定义 {
        规则名 = '下坡结束判断'
        拒绝 { jj_最新历史价 == null || jj_历史最高价_近6月 == null || jj_历史最高价_近3月 == null }

        操作 {
            if (
                //曲线一直往下走
                jj_历史最高价_近6月 - jj_历史最高价_近3月 > 0.05 && 
                jj_历史最高价_近3月 - jj_历史最高价_近1月 > 0.05 && 
                jj_历史最高价_近1月 >= jj_历史最高价_近10次 && 
                jj_历史最高价_近1月 > jj_最新历史价 &&
                //一年内价格差距要大
                jj_历史最高价_近12月 - jj_最新历史价 > 0.3 &&
                //最新的坡低, 不能太高于前面那个坡低
                jj_历史最低价_近12月 - jj_历史最低价_近1月 < 0.2 &&
                //代表开始回转
                jj_最新历史价 > jj_历史最低价_近10次
                // 最近一个月是否是下坡 TODO
            ) {
                // INFO("下坡结束: $jj_code : $jj_名")
                下坡结束 = true
                钉钉消息("下坡结束: $jj_code : $jj_名")
            }
        }
    }
}

函数定义("钉钉消息") {String msg -> 
    JSONObject msgJo = new JSONObject(3)
    msgJo.put("msgtype", "text")
    msgJo.put("text", new JSONObject(1).fluentPut("content", "Fund: " + msg))
    msgJo.put("at", new JSONObject(1).fluentPut("isAtAll", false))
    Utils.http()
        .post("https://oapi.dingtalk.com/robot/send?access_token=7e9d8d97e6b5e76a6a07b0c5d7c31e82f0fbdb8ced1ac23168f9fd5c28c57f1f")
        .jsonBody(msgJo.toString()).execute()
}
```
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
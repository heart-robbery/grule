package service.rule.spec

import service.rule.DecisionContext

/**
 * 评分卡spec
 * 例子:
评分卡 {
 评分卡名 = "测试评分卡"
 基础分 = 600
 模型 = [
     ['年龄', 18..24, 40],
     ['年龄', 25..45, 80],
     ['年龄', 46..60, 50],
     ['性别', 'F', 50],
     ['性别', 'M', 60],
     ['工作成市', ['成都', '巴中'], 99]
 ]
 赋值变量 = '评分结果'
}
 */
class ScorecardSpec extends BaseSpec {
    /**
     * 评分卡名字
     */
    String 评分卡名
    /**
     * 可要可不要
     */
    Number 基础分
    /**
     [
         ['年龄', 18..24, 40],
         ['年龄', 25..45, 80],
         ['年龄', 46..60, 50],
         ['性别', 'F', 50],
         ['性别', 'M', 60]
     ]
     */
    List<List> 模型
    /**
     * 评分结果存放在哪个变量里面
     */
    String 赋值变量


    /**
     * 计算结果
     * 按模型顺序依次计算结果并累加
     * @param ctx
     */
    def compute(DecisionContext ctx) {
        def result = (基础分?:0) + 模型.findResults {item ->
            def attrValue = ctx.data.get(item[0])
            def enumValue = item[1]
            if (enumValue instanceof Range || enumValue instanceof Collection) {
                if (attrValue in enumValue) return item[2]
            } else if (attrValue == enumValue) return item[2]
            0
        }.sum()
        ctx.setAttr(赋值变量, result)
        return result
    }


    @Override
    String name() { return 评分卡名 }
}

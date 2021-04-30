package service.rule

/**
 * 评分卡spec
 * 例子:
 评分卡 {
     基础分 = 600
     模型 = [
         ['年龄', 18..24, 40],
         ['年龄', 25..45, 80],
         ['年龄', 46..60, 50],
         ['性别', 'F', 50],
         ['性别', 'M', 60]
     ]
     赋值变量 = '评分结果'
 }
 */
class ScorecardSpec {
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
     * @param data
     */
    def compute(Map data) {
        模型.findResults {item ->
            def attrValue = data.get(item[0])
            def enumValue = item[2]
            if (enumValue instanceof Range || enumValue instanceof Collection) {
                if (attrValue in enumValue) return item[3]
            } else if (attrValue == enumValue) return item[3]
            0
        }.sum()
    }
}

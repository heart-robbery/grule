package entity

enum FieldType {
    // 默认高精度计算
    Str("字符串", String), Decimal("小数", BigDecimal), Int("整数", Long), Bool("布尔", Boolean), Time("时间", Date)
    String cnName
    Class clzType

    FieldType(String cnName, Class clzType) {
        this.cnName = cnName
        this.clzType = clzType
    }
}

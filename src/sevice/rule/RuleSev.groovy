package sevice.rule

import core.module.ServerTpl

class RuleSev extends ServerTpl {


    def rule(@DelegatesTo(RuleSpec) Closure cl) {

    }


    class RuleSpec {
        String id
        String name

    }


    class RuleContext {
        final Map attr = new HashMap()

        def get(String pName) {attr[(pName)]}

//        String getStr(String pName) {
//            if (attr.containsKey(pName)) return attr[(pName)]
//            return null
//        }
//
//        Boolean getBoolean(String pName) {
//            if (attr.containsKey(pName)) return Boolean.valueOf(attr[(pName)])
//            return null
//        }
//
//        Integer getInteger(String pName) {
//            if (attr.containsKey(pName)) return Integer.valueOf(attr[(pName)])
//            return null
//        }
//
//        Double getDouble(String pName) {
//            if (attr.containsKey(pName)) return Double.valueOf(attr[(pName)])
//            return null
//        }
    }


    /**
     * 条件链
     */
    class ConditionSpecChain {
        private String             property
        private String             op
        private Object             value
        private ConditionSpecChain next
        private String             nextOp

        ConditionSpecChain and(@DelegatesTo(ConditionSpecChain) Closure cl) {
            next = new ConditionSpecChain()
            def code = cl.rehydrate(next)
            cl.resolveStrategy = Closure.DELEGATE_ONLY
            code()
            nextOp = "and"
            return next
        }

        ConditionSpecChain or(@DelegatesTo(ConditionSpecChain) Closure cl) {
            next = new ConditionSpecChain()
            def code = cl.rehydrate(next)
            cl.resolveStrategy = Closure.DELEGATE_ONLY
            code()
            nextOp = "or"
            return next
        }

        boolean eval(RuleContext ctx) {
            def v = ctx.get(property)
            if (v == null) return false // 不存在默认返回false

            // 计算
            boolean f
            try {
                if ("==" == op) f = (v == value)
                else if (">" == op) f = (((Number) v) <=> (Number) value) > 0 ? true : false
                else if ("<" == op) f = (((Number) v) <=> (Number) value) > 0 ? true : false
                else if (">=" == op) f = (((Number) v) <=> (Number) value) > 0 ? true : false
                else if ("<=" == op) f = (((Number) v) <=> (Number) value) > 0 ? true : false
                else if ("contans" == op) f = (((Number) v) <=> (Number) value) > 0 ? true : false
            } catch (Exception ex) {
                throw new RuntimeException("'$property':$v $op $value", ex)
            }


            if (next != null) {
                if (f && 'and' == nextOp) f = next.eval(ctx)
                else if (!f && 'or' == nextOp) f = next.eval(ctx)
            }
            return f
        }

        ConditionSpecChain "=="(String field, Object value) {
            new ConditionSpecChain(property: field, op: "==", value: value)
        }
    }

}

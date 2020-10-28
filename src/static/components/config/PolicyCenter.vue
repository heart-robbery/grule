<style scoped>

</style>
<template>
    <div>
        <h-tabs v-model="tabs.type" :datas="types"></h-tabs>
        <keep-alive>
            <component v-bind:is="tabs.type" :tabs="tabs" :menu="menu"></component>
        </keep-alive>
    </div>
</template>
<script>
    module.exports = {
        props: ['menu'],
        data() {
            return {
                tabs: {
                    type: localStorage.getItem('rule.policyConfig.tab') || 'DecisionConfig',
                    showId: null
                },
                types: {DecisionConfig: '决策列表', FieldConfig:'字段列表', DataCollectorConfig: '数据集成', OpHistory: '操作历史'
                    // PolicyConfig: '策略列表', RuleConfig: '规则列表'
                },
            }
        },
        activated() {
            this.tabs.type = localStorage.getItem('rule.policyConfig.tab') || 'DecisionConfig'
        },
        watch: {
            'tabs.type': function (v) {
                localStorage.setItem("rule.policyConfig.tab", v);
            }
        }
    }
</script>
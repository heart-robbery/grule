<style scoped>

</style>
<template>
    <div>
        <h-tabs v-model="tabs.type" :datas="types"></h-tabs>
        <keep-alive>
            <component v-bind:is="tabs.type" :tabs="tabs"></component>
        </keep-alive>
    </div>
</template>
<script>
    module.exports = {
        data() {
            return {
                tabs: {
                    type: 'DecisionConfig',
                    showId: null
                },
                types: {DecisionConfig: '决策列表', FieldConfig:'字段列表', DataCollectorConfig: '数据集成', PolicyConfig: '策略列表', RuleConfig: '规则列表'},

            }
        },
    }
</script>
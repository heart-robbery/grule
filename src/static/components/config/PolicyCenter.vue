<style scoped>

</style>
<template>
    <div>
        <h-tabs v-model="type" :datas="types"></h-tabs>
        <keep-alive>
            <component v-bind:is="type"></component>
        </keep-alive>
    </div>
</template>
<script>
    module.exports = {
        data() {
            return {
                type: 'DecisionConfig',
                types: {DecisionConfig: '决策列表', PolicyConfig: '策略列表', RuleConfig: '规则列表'}
            }
        }
    }
</script>
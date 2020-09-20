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
    loadJs('moment');
    module.exports = {
        data() {
            return {
                tabs: {
                    type: localStorage.getItem('rule.dataCenter.tab') || 'DecisionResult',
                    showId: null
                },
                types: {DecisionResult: '决策结果'},
            }
        },
        watch: {
            'tabs.type': function (v) {
                localStorage.setItem("rule.dataCenter.tab", v);
            }
        }
    }
</script>
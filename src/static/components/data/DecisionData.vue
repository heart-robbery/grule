<template>
    <div>
        <h-tabs v-model="tabs.type" :datas="types"></h-tabs>
        <keep-alive>
            <component v-bind:is="tabs.type" :tabs="tabs" :menu="menu"></component>
        </keep-alive>
    </div>
</template>
<script>
    loadJs('moment');
    module.exports = {
        props: ['menu'],
        data() {
            return {
                tabs: {
                    type: localStorage.getItem('rule.dataCenter.tab') || 'DecisionResult',
                    showId: null
                },
                types: {DecisionResult: '决策记录', CollectResult: '收集记录'},
            }
        },
        activated() {
            this.tabs.type = localStorage.getItem('rule.dataCenter.tab') || 'DecisionResult'
        },
        watch: {
            'tabs.type': function (v) {
                localStorage.setItem("rule.dataCenter.tab", v);
            }
        }
    }
</script>
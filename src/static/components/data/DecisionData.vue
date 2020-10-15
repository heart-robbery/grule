<template>
    <div>
        <h-tabs v-model="tabs.type" :datas="types"></h-tabs>
        <keep-alive>
            <component v-bind:is="tabs.type" :tabs="tabs" :menu="menu"></component>
        </keep-alive>
    </div>
</template>
<script>
    // loadJs('moment');
    module.exports = {
        props: ['menu'],
        data() {
            let types = {DecisionResult: '决策记录', CollectResult: '收集记录'};
            let type = localStorage.getItem('rule.dataCenter.tab')  || 'DecisionResult';
            if (!types[(type)]) {
                type = 'DecisionResult';
                localStorage.setItem('rule.dataCenter.tab', 'DecisionResult')
            }
            return {
                tabs: {
                    type: type,
                    showId: null
                },
                types: types,
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
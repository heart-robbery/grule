<template>
    <div>
        <div>
            <h-tabs v-model="tabs.type" :datas="types"></h-tabs>
            <button v-if="sUser.permissionIds.find((p) => p === 'grant')" style="position: absolute;right: 30px;top:2px;" class="h-btn h-btn-text-red" @click="cleanExpire">
                <i class="h-icon-trash"></i><span>数据清理</span>
            </button>
        </div>
        <keep-alive>
            <component v-bind:is="tabs.type" :tabs="tabs" :menu="menu"></component>
        </keep-alive>
    </div>
</template>
<script>
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
                sUser: app.$data.user,
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
        },
        methods: {
            cleanExpire() {
                $.ajax({
                    url: 'mnt/cleanExpire',
                    success: (res) => {
                        if (res.code === '00') {
                            this.$Message.success(res.desc);
                        } else {
                            this.$Message.error(res.desc);
                        }
                    }
                })
            }
        }
    }
</script>
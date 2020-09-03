<style scoped>

</style>
<template>
    <div class="h-panel">
        <div class="h-panel-bar">
            <span class="h-panel-title">策略详情</span>
            <span v-color:gray v-font="13">说明~~</span>
            <div class="h-panel-right">
                <h-search placeholder="查询" v-width="200"></h-search>
                <i class="h-split"></i>
                <button class="h-btn h-btn-green h-btn-m">查询</button>
            </div>
        </div>
        <div class="h-panel-body">
        </div>
    </div>
</template>
<script>
    module.exports = {
        prop: ['policyId'],
        data() {
            return {

            }
        },
        mounted() {
            this.load()
        },
        methods: {
            load() {
                $.ajax({
                    url: 'mnt/policyDetail/' + this.policyId,
                    success: (res) => {
                        if (res.code == '00') {

                        } else this.$Notice({type: 'error', content: res.desc, timeout: 5})
                    }
                })
            }
        }
    }
</script>
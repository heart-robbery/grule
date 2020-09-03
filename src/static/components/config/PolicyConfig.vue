<style scoped>

</style>
<template>
    <div class="h-panel">
        <div class="h-panel-bar">
            <span class="h-panel-title">策略集</span>
            <span v-color:gray v-font="13">说明~~</span>
            <div class="h-panel-right">
                <h-search placeholder="查询" v-width="200"></h-search>
                <i class="h-split"></i>
                <button class="h-btn h-btn-green h-btn-m">查询</button>
            </div>
        </div>
        <div class="h-panel-body">
            <h-table ref="table" :datas="policy.list" stripe select-when-click-tr :loading="policyLoading">
                <h-tableitem title="ID" prop="policyId" align="center"></h-tableitem>
                <h-tableitem title="策略名" prop="name" align="center"></h-tableitem>
                <h-tableitem title="描述说明" prop="comment" align="center"></h-tableitem>
                <h-tableitem title="操作" align="center" :width="100">
                    <template slot-scope="{data}">
                        <span class="text-hover" @click="openPolicy(data)">{{data._expand?'收起':'展开'}}</span>
                        <span class="text-hover" >测试</span>
                        &nbsp;
                        <span class="text-hover" @click="removePolicy(data)">删除</span>
                    </template>
                </h-tableitem>

                <!-- 下拉展示 -->
                <template slot="expand" slot-scope="{index, data}">
                    {{data.policyId}}
<!--                    <Form readonly mode="twocolumn">-->
<!--                        <FormItem label="序号">{{index}}</FormItem>-->
<!--                        <FormItem label="姓名">{{data.name}}</FormItem>-->
<!--                        <FormItem label="年龄">{{data.age}}</FormItem>-->
<!--                        <FormItem label="地址">{{data.address}}</FormItem>-->
<!--                    </Form>-->
<!--                    <Loading :loading="data.loading"></Loading>-->
                </template>

                <div slot="empty">暂时无数据</div>
            </h-table>
        </div>
        <div v-if="policy.totalRow" class="h-panel-bar">
            <h-pagination :cur="policy.page" :total="policy.totalRow" :size="policy.pageSize"
                          align="right" @change="load" layout="pager,total"></h-pagination>
        </div>
    </div>
</template>
<script>
    module.exports = {
        data: function() {
            let list = [
                {policyId: 'policyId1', name:'policyId1', comment:'policyId1'},
                {policyId: 'policyId2', name:'policyId2', comment:'policyId2'},
                {policyId: 'policyId3', name:'policyId3', comment:'policyId3'},
            ];
            return {
                policyLoading: false,
                policy: {
                    page: 1,
                    pageSize: 2,
                    totalRow: list.length,
                    list: list
                }
            };
        },
        mounted: function () {
            this.load()
        },
        methods: {
            removePolicy(data) {
                this.$Confirm('确定删除？', `删除策略: ${data.policyId}`).then(() => {
                    $.ajax({
                        url: 'mnt/deletePolicy/' + data.policyId,
                        success: (res) => {
                            if (res.code == '00') {
                                this.$Message.success('删除成功');
                                this.load();
                            } else this.$Notice({type: 'error', content: res.desc, timeout: 5})
                        }
                    });
                }).catch(() => {
                    this.$Message.error('取消');
                });
            },
            openPolicy(data) {
                this.$set(data, '_expand', !data._expand);
            },
            load(page) {
                if (page == undefined || page == null) page = {page: 1};
                this.policyLoading = true;
                $.ajax({
                    url: 'mnt/policyPage',
                    data: {page: page.page || 1},
                    success: (res) => {
                        this.policyLoading = false;
                        if (res.code == '00') {
                            this.policy = res.data;
                        } else this.$Notice({type: 'error', content: res.desc, timeout: 5})
                    },
                    error: () => {
                        this.policyLoading = false;
                    }
                })
            }
        },
        watch: {
        }
    };
</script>
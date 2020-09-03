<style scoped>

</style>
<template>
    <div class="h-panel">
        <div class="h-panel-bar">
            <span class="h-panel-title">规则集</span>
            <span v-color:gray v-font="13">说明~~</span>
            <div class="h-panel-right">
                <h-search placeholder="查询" v-width="200"></h-search>
                <i class="h-split"></i>
                <button class="h-btn h-btn-green h-btn-m">查询</button>
            </div>
        </div>
        <div class="h-panel-body">
            <h-table ref="table" :datas="rule.list" stripe select-when-click-tr :loading="ruleLoading">
                <h-tableitem title="ID" prop="ruleId" align="center"></h-tableitem>
                <h-tableitem title="策略名" prop="name" align="center"></h-tableitem>
                <h-tableitem title="描述说明" prop="comment" align="center"></h-tableitem>
                <h-tableitem title="操作" align="center" :width="100">
                    <template slot-scope="{data}">
                        <span class="text-hover" @click="openRule(data)">{{data._expand?'收起':'展开'}}</span>
                        <span class="text-hover" >测试</span>
                        &nbsp;
                        <span class="text-hover" @click="removeRule(data)">删除</span>
                    </template>
                </h-tableitem>

                <!-- 下拉展示 -->
                <template slot="expand" slot-scope="{index, data}">
                    {{data.ruleId}}
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
        <div v-if="rule.totalRow" class="h-panel-bar">
            <h-pagination :cur="rule.page" :total="rule.totalRow" :size="rule.pageSize"
                          align="right" @change="load" layout="pager,total"></h-pagination>
        </div>
    </div>
</template>
<script>
    module.exports = {
        data: function() {
            let list = [
                {ruleId: 'ruleId1', name:'ruleId1', comment:'ruleId1'},
                {ruleId: 'ruleId2', name:'ruleId2', comment:'ruleId2'},
                {ruleId: 'ruleId3', name:'ruleId3', comment:'ruleId3'},
            ];
            return {
                ruleLoading: false,
                rule: {
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
            removeRule(data) {
                this.$Confirm('确定删除？', `删除规则: ${data.ruleId}`).then(() => {
                    $.ajax({
                        url: 'mnt/deleteRule/' + data.ruleId,
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
            openRule(data) {
                this.$set(data, '_expand', !data._expand);
            },
            load(page) {
                if (page == undefined || page == null) page = {page: 1};
                this.ruleLoading = true;
                $.ajax({
                    url: 'mnt/rulePage',
                    data: {page: page.page || 1},
                    success: (res) => {
                        this.ruleLoading = false;
                        if (res.code == '00') {
                            this.rule = res.data;
                        } else this.$Notice({type: 'error', content: res.desc, timeout: 5})
                    },
                    error: () => {
                        this.ruleLoading = false;
                    }
                })
            }
        },
        watch: {
        }
    };
</script>
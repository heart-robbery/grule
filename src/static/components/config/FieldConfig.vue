<template>
    <div class="h-panel">
        <div class="h-panel-bar">
            <span class="h-panel-title">决策集</span>
            <!--            <span v-color:gray v-font="13">说明~~</span>-->
            &nbsp;&nbsp;
            <h-button @click="add"><i class="h-icon-plus"></i></h-button>
            <div class="h-panel-right">
                <h-search placeholder="查询" v-width="200" v-model="kw" show-search-button search-text="搜索" @search="load"></h-search>
                <!--                <i class="h-split"></i>-->
                <!--                <button class="h-btn h-btn-green h-btn-m" @click="load">查询</button>-->
            </div>
        </div>
        <div class="h-panel-body">
            <h-table ref="table" :datas="list" stripe select-when-click-tr :loading="loading">
<!--                <h-tableitem title="ID" prop="id" align="center"></h-tableitem>-->
                <h-tableitem title="英文名" prop="enName" align="center"></h-tableitem>
                <h-tableitem title="中文名" prop="cnName" align="center"></h-tableitem>
                <h-tableitem title="描述" prop="comment" align="center"></h-tableitem>
                <h-tableitem title="值函数名" prop="dataCollector" align="center"></h-tableitem>
                <h-tableitem title="操作" align="center" :width="100">
                    <template slot-scope="{data}">
                        <span class="text-hover">测试</span>
                        &nbsp;
                        <span class="text-hover" @click="del(data)">删除</span>
                    </template>
                </h-tableitem>

                <!-- 下拉展示 -->
                <template slot="expand" slot-scope="{index, data}">
                    {{data.decisionId}}
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
        <div v-if="totalRow" class="h-panel-bar">
            <h-pagination ref="pagination" :cur="page" :total="totalRow" :size="pageSize"
                          align="right" @change="load" layout="pager,total"></h-pagination>
        </div>
    </div>
</template>
<script>
    module.exports = {
        data() {
            return {
                loading: false,
                page: 1, totalRow: 0, pageSize: 10, list: []
            }
        },
        methods: {
            add() {

            },
            del(field) {
                this.$Confirm('确定删除?', `删除字段: ${field.cnName}`).then(() => {
                    this.$Message(`删除字段: ${field.cnName}`);
                    $.ajax({
                        url: 'mnt/delField/' + field.enName,
                        success: (res) => {
                            if (res.code == '00') {
                                this.$Message.success(`删除字段: ${field.cnName} 成功`);
                                this.load();
                            } else this.$Notice.error(res.desc)
                        }
                    });
                }).catch(() => {
                    this.$Message.error('取消');
                });
            },
            load(page) {
                if (page == undefined || page == null) page = {page: 1};
                this.loading = true;
                $.ajax({
                    url: 'mnt/fieldPage',
                    data: {page: page.page || 1, kw: this.kw},
                    success: (res) => {
                        this.loading = false;
                        if (res.code == '00') {
                            this.decision = res.data;
                        } else this.$Notice.error({content: res.desc, timeout: 5})
                    },
                    error: () => {
                        this.loading = false;
                    }
                })
            }
        }
    }
</script>
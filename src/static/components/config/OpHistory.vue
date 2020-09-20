<template>
    <div class="h-panel">
        <div class="h-panel-bar">
            <!--            <span class="h-panel-title">数据集</span>-->
            <!--            <span v-color:gray v-font="13">说明~~</span>-->
            <h-select v-model="model.type" :datas="types" placeholder="所有" style="width: 150px" @change="load"></h-select>
            <div class="h-panel-right">
                <h-search placeholder="查询" v-width="200" v-model="model.kw" show-search-button search-text="搜索" @search="load"></h-search>
            </div>
        </div>
        <div class="h-panel-body">
            <h-table ref="table" :datas="list" stripe select-when-click-tr :loading="loading">
                <h-tableitem title="类型" prop="tbName" align="center" :width="120" :format="formatType"></h-tableitem>
                <h-tableitem title="操作员" prop="operator" align="center" :width="120"></h-tableitem>
                <h-tableitem title="操作时间" prop="createTime" align="center" :width="130" :format="formatDate"></h-tableitem>
                <h-tableitem title="内容" prop="content" align="center"></h-tableitem>
                <div slot="empty">暂时无数据</div>
            </h-table>
        </div>
        <div v-if="totalRow" class="h-panel-bar">
            <h-pagination ref="pagination" :cur="page" :total="totalRow" :size="pageSize" align="right" @change="load" layout="pager,total"></h-pagination>
        </div>
    </div>
</template>
<script>
    const types = [
        { title: '决策', key: 'Decision'},
        { title: '字段', key: 'RuleField'},
        { title: '数据集成', key: 'DataCollector'},
    ];
    module.exports = {
        data() {
            return {
                types: types,
                model: {kw: null, type: null},
                list: [], totalRow: 0, page: 1, pageSize: 1, loading: false
            }
        },
        mounted() {
            this.load()
        },
        methods: {
            formatType(v) {
                for (let type of types) {
                    if (type.key == 'Decision' && v == 'decision') return type.title;
                    else if (type.key == 'RuleField' && v == 'rule_field') return type.title;
                    else if (type.key == 'DataCollector' && v == 'data_collector') return type.title;
                }
                return v
            },
            formatDate(v) {
                return moment(v).format('YYYY-MM-DD HH:mm:ss')
            },
            load(page) {
                if (page == undefined || page == null) page = {page: 1};
                this.loading = true;
                this.page = 1;
                this.pageSize = 10;
                this.totalRow = 0;
                this.list = [];
                $.ajax({
                    url: 'mnt/opHistoryPage',
                    data: {page: page.page || 1, kw: this.model.kw, type: this.model.type},
                    success: (res) => {
                        this.loading = false;
                        if (res.code == '00') {
                            this.page = res.data.page;
                            this.pageSize = res.data.pageSize;
                            this.totalRow = res.data.totalRow;
                            this.list = res.data.list;
                        } else this.$Notice.error(res.desc)
                    },
                    error: () => this.loading = false
                })
            }
        }
    }
</script>
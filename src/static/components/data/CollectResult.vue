<template>
    <div class="h-panel">
        <div class="h-panel-bar">
            <h-select v-model="model.collectorType" :datas="types" placeholder="类型" style="width: 90px; float: left" @change="load"></h-select>
            <h-select v-model="model.success" :datas="successTypes" placeholder="是否成功" style="width: 90px; float: left" @change="load"></h-select>
            <h-autocomplete v-model="model.decisionId" :option="decisions" style="float:left; width: 150px" @change="load" placeholder="决策名"></h-autocomplete>
            <h-autocomplete v-model="model.collector" :option="collectors" style="float:left; width: 150px" @change="load" placeholder="收集器"></h-autocomplete>
            <input type="number" v-model="model.spend" placeholder=">=耗时(ms)" style="width: 110px" @keyup.enter="load"/>
            <input type="text" v-model="model.decideId" placeholder="流水id(精确匹配)" style="width: 250px" @keyup.enter="load"/>
            <h-datepicker v-model="model.startTime" type="datetime" :has-seconds="true" placeholder="开始时间"></h-datepicker>
            <h-datepicker v-model="model.endTime" type="datetime" :has-seconds="true" placeholder="结束时间"></h-datepicker>
            <button class="h-btn h-btn-primary float-right" @click="load"><i class="h-icon-search"></i><span>查询</span></button>
<!--            <div class="h-panel-right">-->
<!--                <h-search placeholder="查询" v-width="200" v-model="model.kw" show-search-button search-text="搜索" @search="load"></h-search>-->
<!--            </div>-->
        </div>
        <div class="h-panel-body">
            <h-table ref="table" :datas="list" stripe select-when-click-tr :loading="loading"R>
                <!--                <h-tableitem title="ID" prop="id" align="center"></h-tableitem>-->
                <h-tableitem title="决策" prop="decisionName" align="center"></h-tableitem>
                <h-tableitem title="收集器" prop="collectorName" align="center"></h-tableitem>
                <h-tableitem title="类型" prop="collectorType" align="center" :format="formatType"></h-tableitem>
                <h-tableitem title="决策流水" prop="decideId" align="center" :width="250"></h-tableitem>
                <h-tableitem title="收集时间" prop="collectDate" align="center" :format="formatDate"></h-tableitem>
                <h-tableitem title="耗时(ms)" prop="spend" align="center"></h-tableitem>
                <h-tableitem title="成功" prop="success" align="center" :format="formatSuccessType"></h-tableitem>
<!--                <h-tableitem title="描述" prop="comment" align="center"></h-tableitem>-->
                <h-tableitem title="操作" align="center" :width="80">
                    <template slot-scope="{data}">
                        <span class="text-hover" @click="open(data)">{{data._expand?'收起':'展开'}}</span>
                    </template>
                </h-tableitem>
                <template slot="expand" slot-scope="{index, data}">
                    <h-form readonly>
                        <h-formitem v-if="data.collectorType == 'http'" label="接口地址">{{data.url}}</h-formitem>
                        <h-formitem v-if="data.collectorType == 'http'" label="接口返回">{{data.result}}</h-formitem>
                        <h-formitem v-if="data.collectorType == 'http'" label="解析结果">{{data.resolveResult}}</h-formitem>
                        <h-formitem v-if="data.collectorType == 'http'" label="请求异常">{{data.httpException}}</h-formitem>
                        <h-formitem v-if="data.collectorType == 'http'" label="解析异常">{{data.parseException}}</h-formitem>
                        <h-formitem v-if="data.collectorType == 'script'" label="脚本结果">{{data.result}}</h-formitem>
                        <h-formitem v-if="data.collectorType == 'script'" label="脚本异常">{{data.scriptException}}</h-formitem>
                    </h-form>
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
    const types = [
        { title: '接口', key: 'http'},
        { title: '脚本', key: 'script'},
    ];
    const successTypes = [
        { title: '成功', key: 'true'},
        { title: '失败', key: 'false'},
    ];
    module.exports = {
        data() {
            return {
                types: types,
                successTypes: successTypes,
                model: {},
                loading: false,
                page: 1, totalRow: 0, pageSize: 10, list: [],
                decisions: {
                    keyName: 'decisionId',
                    titleName: 'name',
                    minWord: 1,
                    loadData: (filter, cb) => {
                        $.ajax({
                            url: 'mnt/decisionPage',
                            data: {page: 1, pageSize: 5, nameLike: filter},
                            success: (res) => {
                                this.isLoading = false;
                                if (res.code == '00') {
                                    cb(res.data.list.map((r) => {
                                        return {decisionId: r.decisionId, name: r.name}
                                    }))
                                } else this.$Notice.error(res.desc)
                            },
                        });
                    }
                },
                collectors: {
                    keyName: 'enName',
                    titleName: 'cnName',
                    minWord: 1,
                    loadData: (filter, cb) => {
                        $.ajax({
                            url: 'mnt/dataCollectorPage',
                            data: {page: 1, pageSize: 5, kw: filter},
                            success: (res) => {
                                this.isLoading = false;
                                if (res.code == '00') {
                                    cb(res.data.list.map((r) => {
                                        return {enName: r.enName, cnName: r.cnName}
                                    }))
                                } else this.$Notice.error(res.desc)
                            },
                        });
                    }
                },
            }
        },
        mounted() {
            this.load()
        },
        components: {
            // 'add-pop': addEditPop
        },
        methods: {
            formatDate(v) {
                return moment(v).format('YYYY-MM-DD HH:mm:ss')
            },
            formatType(v) {
                for (let type of types) {
                    if (type.key == v) return type.title
                }
                return v
            },
            formatSuccessType(v) {
                for (let type of successTypes) {
                    if (type.key == (v + '')) return type.title
                }
                return v
            },
            open(data) {
                this.$set(data, '_expand', !data._expand);
            },
            load(page) {
                if (page == undefined || page == null) page = {page: 1};
                this.loading = true;
                this.page = 1;
                this.pageSize = 10;
                this.totalRow = 0;
                this.list = [];
                $.ajax({
                    url: 'mnt/collectResultPage',
                    data: $.extend({page: page.page || 1}, this.model),
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
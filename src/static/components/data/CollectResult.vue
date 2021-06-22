<template>
    <div class="h-panel">
        <div class="h-panel-bar">
            <h-select v-model="model.collectorType" :datas="types" placeholder="类型" style="width: 70px; float: left" @change="load"></h-select>
            <h-select v-model="model.success" :datas="successTypes" placeholder="是否成功" style="width: 90px; float: left" @change="load"></h-select>
            <h-select v-model="model.dataSuccess" :datas="boolTypes" placeholder="是否查得" style="width: 90px; float: left" @change="load"></h-select>
            <h-select v-model="model.cache" :datas="boolTypes" placeholder="是否缓存" style="width: 90px; float: left" @change="load"></h-select>
            <h-autocomplete v-model="model.decisionId" :option="decisions" style="float:left; width: 180px" @change="load" placeholder="决策"></h-autocomplete>
            <h-autocomplete v-model="model.collector" :option="collectors" style="float:left; width: 180px" @change="load" placeholder="收集器"></h-autocomplete>
            <input type="number" v-model="model.spend" placeholder="耗时(>=ms)" style="width: 100px" @keyup.enter="load"/>
            <input type="text" v-model="model.decideId" placeholder="流水id(精确匹配)" style="width: 250px" @keyup.enter="load"/>
            <h-datepicker v-model="model.startTime" type="datetime" :option="{minuteStep:2}" :has-seconds="true" placeholder="开始时间" style="width: 160px"></h-datepicker>
            <h-datepicker v-model="model.endTime" type="datetime" :option="{minuteStep:2}" :has-seconds="true" placeholder="结束时间" style="width: 160px"></h-datepicker>
            <button class="h-btn h-btn-primary float-right" @click="load"><span>搜索</span></button>
        </div>
        <div class="h-panel-body">
            <h-table ref="table" :datas="list" stripe select-when-click-tr :loading="loading" border>
                <h-tableitem title="决策" align="center">
                    <template slot-scope="{data}">
                        <a v-if="data.decisionName" href="javascript:void(0)" @click="jumpToDecision(data)">{{data.decisionName}}</a>
                        <span v-else>{{data.decisionId}}</span>
                    </template>
                </h-tableitem>
                <h-tableitem title="收集器" align="center">
                    <template slot-scope="{data}">
                        <a v-if="data.collectorName" href="javascript:void(0)" @click="jumpToDataCollector(data)">{{data.collectorName}}</a>
                        <span v-else>{{data.collector}}</span>
                    </template>
                </h-tableitem>
                <h-tableitem title="类型" prop="collectorType" align="center" :format="formatType" :width="70"></h-tableitem>
                <h-tableitem title="决策流水" align="center" :width="250">
                    <template slot-scope="{data}">
                        <a href="javascript:void(0)" @click="model.decideId = data.decideId; load()">{{data.decideId}}</a>
                    </template>
                </h-tableitem>
                <h-tableitem title="收集时间" align="center" :width="135">
                    <template slot-scope="{data}">
                        <date-item :time="data.collectDate" />
                    </template>
                </h-tableitem>
                <h-tableitem title="耗时(ms)" prop="spend" align="center" :width="70"></h-tableitem>
                <h-tableitem title="状态" prop="status" align="center" :format="formatStatusType" :width="60"></h-tableitem>
                <h-tableitem title="查得" prop="dataStatus" align="center" :format="formatDataStatusType" :width="40"></h-tableitem>
                <h-tableitem title="缓存" prop="cache" align="center" :format="formatBoolType" :width="40"></h-tableitem>
                <h-tableitem title="操作" align="center" :width="70">
                    <template slot-scope="{data}">
                        <span class="text-hover" style="color: #9bbdef" @click="open(data)">{{data._expand?'收起':'展开'}}</span>
                    </template>
                </h-tableitem>
                <template slot="expand" slot-scope="{index, data}">
                    <h-form readonly>
                        <h-formitem v-if="data.collectorType == 'http'" label="接口地址">{{data.url}}</h-formitem>
                        <h-formitem label="收集结果">{{data.result}}</h-formitem>
                        <h-formitem label="执行异常">{{data.exception}}</h-formitem>
                        <h-formitem v-if="data.collectorType == 'http'" label="解析结果"><code>{{data.resolveResult}}</code></h-formitem>
                        <h-formitem v-if="data.collectorType == 'http'" label="解析异常">{{data.resolveException}}</h-formitem>
                    </h-form>
                </template>
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
        { title: '接口', key: 'http'},
        { title: '脚本', key: 'script'},
        { title: 'SQL', key: 'sql'},
    ];
    const successTypes = [
        { title: '成功', key: 'true'},
        { title: '失败', key: 'false'},
    ];
    const boolTypes = [
        { title: '是', key: 'true'},
        { title: '否', key: 'false'},
    ];
    module.exports = {
        props: ['tabs', 'menu'],
        data() {
            return {
                types: types,
                successTypes: successTypes,
                boolTypes: boolTypes,
                model: {startTime: (function () {
                        let d = new Date();
                        let month = d.getMonth() + 1;
                        return d.getFullYear() + "-" + (month < 10 ? '0' + month : month) + "-" + (d.getDate() < 10 ? '0' + d.getDate() : d.getDate()) + " 00:00:00"
                    })()
                },
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
                                if (res.code === '00') {
                                    cb(res.data.list.map((r) => {
                                        return {decisionId: r.id, name: r.name}
                                    }))
                                } else this.$Notice.error(res.desc)
                            },
                        });
                    }
                },
                collectors: {
                    keyName: 'id',
                    titleName: 'name',
                    minWord: 1,
                    loadData: (filter, cb) => {
                        $.ajax({
                            url: 'mnt/dataCollectorPage',
                            data: {page: 1, pageSize: 5, kw: filter},
                            success: (res) => {
                                this.isLoading = false;
                                if (res.code === '00') {
                                    cb(res.data.list.map((r) => {
                                        return {id: r.id, name: r.name}
                                    }))
                                } else this.$Message.error(res.desc)
                            },
                        });
                    }
                },
            }
        },
        mounted() {
            this.initQuery()
            this.load()
        },
        activated() {
            if (this.initQuery()) {
                this.load()
            }
            // if (
            //     app.$data.tmp.testResultId &&
            //     !this.model.decideId &&
            //     this.list && this.list.filter(o => o.decideId == app.$data.tmp.testResultId).length == 0
            // ) {
            //     this.load()
            // }
        },
        watch: {
            'model.startTime'() {
                this.load()
            },
            'model.endTime'() {
                this.load()
            }
        },
        methods: {
            initQuery() {
                let changed = false
                if (this.tabs.decideId && this.tabs.decideId !== this.model.decideId) {
                    this.model.decideId = this.tabs.decideId
                    changed = true
                }
                if (this.tabs.startTime && this.tabs.startTime !== this.model.startTime) {
                    this.model.startTime = this.tabs.startTime
                    changed = true
                }
                this.tabs.decideId = null;
                this.tabs.startTime = null;
                return changed
            },
            jumpToDataCollector(item) {
                this.tabs.showId = item.collector;
                this.tabs.type = 'DataCollectorConfig';
            },
            jumpToDecision(item) {
                this.tabs.showId = item.decisionId;
                this.tabs.type = 'DecisionConfig';
            },
            formatType(v) {
                for (let type of types) {
                    if (type.key == v) return type.title
                }
                return v
            },
            formatStatusType(v) {
                if ('0000' === v) return '成功';
                return '失败';
            },
            formatDataStatusType(v) {
                if ('0000' === v) return '是';
                return '否';
            },
            formatBoolType(v) {
                if (v === true) return '是';
                return '否';
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
                        if (res.code === '00') {
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
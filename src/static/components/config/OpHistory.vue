<style scoped>
    .h-text-ellipsis-limit-text {
        white-space: pre-wrap;
    }
</style>
<template>
    <div class="h-panel">
        <div class="h-panel-bar">
            <h-select v-model="model.type" :datas="types" placeholder="所有" style="width: 100px; float: left" @change="load"></h-select>
            <input type="text" v-model="model.kw" placeholder="关键词" @keyup.enter="load"/>
            <h-datepicker v-model="model.startTime" type="datetime" :option="{minuteStep:5}" :has-seconds="true" placeholder="开始时间" style="width: 160px"></h-datepicker>
            <h-datepicker v-model="model.endTime" type="datetime" :option="{minuteStep:5}" :has-seconds="true" placeholder="结束时间" style="width: 160px"></h-datepicker>
            <div class="h-panel-right">
                <button class="h-btn h-btn-primary float-right" @click="load"><i class="h-icon-search"></i><span>搜索</span></button>
            </div>
        </div>
        <div class="h-panel-body">
            <h-table ref="table" :datas="list" stripe select-when-click-tr :loading="loading" border>
                <h-tableitem title="类型" prop="tbName" align="center" :width="70" :format="formatType"></h-tableitem>
                <h-tableitem title="操作员" prop="operator" align="center" :width="100"></h-tableitem>
                <h-tableitem title="操作时间" align="center" :width="135">
                  <template slot-scope="{data}">
                    <date-item :time="data.createTime" />
                  </template>
                </h-tableitem>
                <h-tableitem title="内容" align="center">
                    <template slot-scope="{data}">
                        <h-textellipsis :text="data.content" :height="150" :isLimitHeight="data._isLimitHeight">
                            <template v-if="data._isLimitHeight" slot="more"><span>...</span><span class="link" @click="$Clipboard({text: data.content})">复制</span></template>
<!--                            <span v-else slot="after" class="link" @click="data._isLimitHeight=true">收起</span>-->
                        </h-textellipsis>
                    </template>
                </h-tableitem>
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
        { title: '收集器', key: 'DataCollector'},
    ];
    module.exports = {
        data() {
            return {
                types: types,
                model: {kw: null, type: null, startTime: (function () {
                        let d = new Date(new Date().getTime() - 1000 * 60 * 60 * 24 * 30); //默认显示前30天
                        let month = d.getMonth() + 1;
                        return d.getFullYear() + "-" + (month < 10 ? '0' + month : month) + "-" + (d.getDate() < 10 ? '0' + d.getDate() : d.getDate()) + " 00:00:00"
                    })()},
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
            load(page) {
                if (page == undefined || page == null) page = {page: 1};
                this.loading = true;
                this.page = 1;
                this.pageSize = 10;
                this.totalRow = 0;
                this.list = [];
                $.ajax({
                    url: 'mnt/opHistoryPage',
                    data: $.extend({page: page.page || 1, pageSize: 4}, this.model),
                    success: (res) => {
                        this.loading = false;
                        if (res.code === '00') {
                            this.page = res.data.page;
                            this.pageSize = res.data.pageSize;
                            this.totalRow = res.data.totalRow;
                            res.data.list.forEach((item, index) => item._isLimitHeight = true);
                            this.list = res.data.list;
                        } else this.$Notice.error(res.desc)
                    },
                    error: () => this.loading = false
                })
            }
        }
    }
</script>
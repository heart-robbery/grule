<style>
.policyTr {
    background-color: #eae5e5
}
</style>
<template>
    <div class="h-panel">
        <div class="h-panel-bar">
            <h-form mode="threecolumn">
                <h-formitem>
                    <h-select v-model="model.result" :datas="types" placeholder="决策结果" @change="load"></h-select>
                </h-formitem>
                <h-formitem>
                    <h-autocomplete v-model="model.decisionId" :option="decisionAc" @change="load" placeholder="决策"></h-autocomplete>
                </h-formitem>
                <h-formitem>
                    <input type="text" v-model="model.id" placeholder="流水id(精确)" style="width: 250px" @keyup.enter="load"/>
                </h-formitem>
                <h-formitem>
                    <input type="number" v-model="model.spend" placeholder="耗时(>=ms)" @keyup.enter="load"/>
                </h-formitem>
                <h-formitem>
                    <input type="text" v-model="model.exception" placeholder="异常信息(模糊)" @keyup.enter="load"/>
                </h-formitem>
                <h-formitem>
                    <h-datepicker v-model="model.startTime" type="datetime" :has-seconds="true" placeholder="开始时间"></h-datepicker>
                </h-formitem>
                <h-formitem>
                    <h-datepicker v-model="model.endTime" type="datetime" :has-seconds="true" placeholder="结束时间"></h-datepicker>
                </h-formitem>
                <h-formitem>
                    <button class="h-btn h-btn-primary" @click="load"><span>搜索</span></button>
                </h-formitem>

                <h-formitem single v-for="(item, index) of model.attrFilters" :key="index">
                    <div class="h-input-group">
                        <h-autocomplete v-model="item.fieldId" :option="item.fieldAc" placeholder="字段属性名" ></h-autocomplete>
                        <h-select v-model="item.op" :datas="ops" placeholder="比较符" :deletable="false" @change="(item.op === 'desc' || item.op === 'asc') ? load() : null"></h-select>
                        <input v-if="item.op !== 'desc' && item.op !== 'asc'" type="text" v-model="item.value" placeholder="属性值" @keyup.enter="load"/>
                        <div style="width: 70px; margin-left: 15px">
                            <span class="h-icon-minus" @click="delAttrFilter(item)"></span>&nbsp;
                            <span v-if="model.attrFilters.length === (index + 1)" class="h-icon-plus" @click="addAttrFilter"></span>
                        </div>
                    </div>
                </h-formitem>
            </h-form>
        </div>
        <div class="h-panel-body">
            <h-table ref="table" :datas="list" stripe select-when-click-tr :loading="loading" @trdblclick="showDetail" border>
                <h-tableitem title="决策" align="center">
                    <template slot-scope="{data}">
                        <a v-if="data.decisionName" href="javascript:void(0)" @click="jumpToDecision(data)">{{data.decisionName}}</a>
                        <span v-else>{{data.decisionId}}</span>
                    </template>
                </h-tableitem>
                <h-tableitem title="流水id" align="center">
                    <template slot-scope="{data}">
                        <a href="javascript:void(0)" @click="jumpToCollectRecord(data)">{{data.id}}</a>
                    </template>
                </h-tableitem>
                <h-tableitem title="结果" prop="result" align="center" :format="formatType" :width="70"></h-tableitem>
                <h-tableitem title="决策时间" align="center" :width="135">
                    <template slot-scope="{data}">
                        <date-item :time="data.occurTime" />
                    </template>
                </h-tableitem>
                <h-tableitem title="耗时(ms)" prop="spend" align="center" :width="70"></h-tableitem>
                <h-tableitem title="异常信息" prop="exception" align="center"></h-tableitem>
                <h-tableitem title="操作" align="center" :width="70">
                    <template slot-scope="{data}">
                        <span class="text-hover" style="color: #9bbdef" @click="showDetail(data)">详情</span>
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
        { title: '拒绝', key: 'Reject'},
        { title: '通过', key: 'Accept'},
        { title: '人工', key: 'Review'},
    ];
    const ops = [
        { title: '等于', key: '='},
        { title: '大于', key: '>'},
        { title: '大于等于', key: '>='},
        { title: '小于', key: '<'},
        { title: '小于等于', key: '<='},
        { title: '包含', key: 'contains'},
        { title: '降序', key: 'desc'},
        { title: '升序', key: 'asc'},
    ];
    const detail = {
        props: ['item'],
        template:`
            <div ref="detailDiv" style="overflow: auto; height: 100vh">
                <header class="h-modal-header text-center">{{title}}</header>
                <h-layout>
                    <h-header style="max-height: 30vh">
                        <h-form readonly>
                            <h-formitem label="入参">{{item.input}}</h-formitem>
                        </h-form>
                    </h-header>
                    <h-layout>
                        <h-sider style="flex: none; max-width: 60%; width: auto">
                            <div class="h-panel">
                                <div class="h-panel-bar">执行策略/规则集
                                    <div class="h-panel-right">
                                        <Search placeholder="策略/规则名" v-width="200" @search="ruleFilter" v-model="ruleKw"></Search>
                                        <i class="h-split"></i><button class="h-btn h-btn-green h-btn-m" @click="ruleFilter">查询</button>
                                    </div>
                                </div>
                                <div class="h-panel-body">
                                    <h-table ref="policyTb" :datas="policies" select-when-click-tr border style="max-height: 70vh; overflow: auto" :getTrClass="getTrClass">
                                        <h-tableitem title="属性" :width="220" align="left" treeOpener>
                                            <template slot-scope="{data}">
                                                <h-form readonly>
                                                    <h-formitem v-for="(v,k) in data.attrs" :key="k" :label="k">{{v}}</h-formitem>
                                                </h-form>
                                            </template>
                                        </h-tableitem>
                                        <h-tableitem title="结果" prop="result" align="center" :width="80" :format="formatType"></h-tableitem>
                                        <h-tableitem title="数据" align="left">
                                            <template slot-scope="{data}">
                                                <h-form readonly :labelWidth="170">
                                                    <h-formitem v-for="item in data.data" :key="item.enName" :label="item.cnName ? item.cnName : item.enName">{{item.value}}</h-formitem>
                                                </h-form>
                                            </template>
                                        </h-tableitem>
                                        <div slot="empty">无策略/规则</div>
                                    </h-table>
                                </div>
                            </div>
                        </h-sider>
                        <h-content>
                            <div class="h-panel">
                                <div class="h-panel-bar">属性结果集
                                    <div class="h-panel-right">
                                        <Search placeholder="属性名" v-width="200" @search="attrFilter" v-model="attrKw"></Search>
                                        <i class="h-split"></i><button class="h-btn h-btn-green h-btn-m" @click="attrFilter">查询</button>
                                    </div>
                                </div>
                                <div class="h-panel-body">
                                    <h-table :datas="attrs" stripe select-when-click-tr border style="max-height: 70vh; overflow: auto">
                                        <h-tableitem title="属性名" :width="100" align="right">
                                            <template slot-scope="{data}">
                                                <span>{{data.cnName ? data.cnName : data.enName}}</span>
                                            </template>
                                        </h-tableitem>
                                        <h-tableitem title="属性值" prop="value" align="left" :width="120"></h-tableitem>
                                        <div slot="empty">无属性</div>
                                    </h-table>
                                </div>
                            </div>
                        </h-content>
                    </h-layout>
                    <h-footer v-if="dataCollectResult">
                        <h-form readonly>
                            <h-formitem label="数据收集">
                                <ace-json v-model="dataCollectResult" style="height: 30vh; width: 99%; max-height: 60%" :readonly="true"></ace-json>
                            </h-formitem>
                        </h-form>
                    </h-footer>
                </h-layout>
            </div>
        `,
        data() {
            if (this.item.detail && this.item.detail.policies) {
                this.item.detail.policies.map(p => p.children = p.items)
            }
            return {
                ruleKw: null,
                attrKw: null,
                attrs: this.item.data,
                policies: this.item.detail ? this.item.detail.policies : null,
                dataCollectResult: this.item.dataCollectResult ? JSON.stringify(this.item.dataCollectResult, null, 2) : null
            }
        },
        mounted() {
            if (this.$refs.policyTb) this.$refs.policyTb.foldAll();
            this.$nextTick(() => this.$refs.policyTb.expandAll());
            document.onkeyup = (e) => {
                if (e.code === 'Escape') this.$emit('close');
            }
        },
        computed: {
            title: function () {
                return this.item.decisionName + ' ' + this.item.id + ' ' + this.formatType(this.item.result);
            }
        },
        methods: {
            getTrClass(data, index) {
                if (data.attrs['策略名']) {
                    return ['policyTr'];
                }
            },
            formatType(v) {
                for (let type of types) {
                    if (type.key == v) return type.title
                }
                return v
            },
            ruleFilter() {
                this.$refs.policyTb.foldAll();
                if (this.ruleKw && this.item.detail.policies) {
                    this.policies = this.item.detail.policies.filter((item, index, arr) => {
                        item.children = item.items.filter(o => {
                            if (o.attrs['规则名'] && o.attrs['规则名'].indexOf(this.ruleKw) >= 0) return true
                            if (o.attrs['评分卡名'] && o.attrs['评分卡名'].indexOf(this.ruleKw) >= 0) return true
                            if (o.attrs['决策名'] && o.attrs['决策名'].indexOf(this.ruleKw) >= 0) return true
                            return false
                        })
                        if (item.attrs.策略名 && item.attrs.策略名.indexOf(this.ruleKw) === -1 && item.children.length === 0) return false;
                        return true;
                    });
                } else {
                    this.item.detail.policies.map(p => p.children = p.items)
                    this.policies = this.item.detail.policies;
                }
                this.$nextTick(() => {
                    this.$refs.policyTb.expandAll();
                })
            },
            attrFilter() {
                if (this.attrKw && this.item.data) {
                    this.attrs = this.item.data.filter((item, index, arr) => {
                        if (item.enName && item.enName.toLowerCase().indexOf(this.attrKw.toLowerCase()) >= 0) return true
                        if (item.cnName && item.cnName.indexOf(this.attrKw) >= 0) return true
                        return false
                    })
                } else this.attrs = this.item.data;
            }
        }
    };
    module.exports = {
        props: ['tabs', 'menu'],
        data() {
            return {
                types: types, ops: ops,
                decisionAc: {
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
                model: {startTime: (function () {
                        let d = new Date();
                        let month = d.getMonth() + 1;
                        return d.getFullYear() + "-" + (month < 10 ? '0' + month : month) + "-" + (d.getDate() < 10 ? '0' + d.getDate() : d.getDate()) + " 00:00:00"
                    })(), attrFilters: [{fieldId: null, fieldAc: this.fieldAc(), op: '=', value: null}]
                },
                list: [], totalRow: 0, page: 1, pageSize: 1, loading: false
            }
        },
        mounted() {
            this.load()
        },
        activated() {
            if (
                app.$data.tmp.testResultId &&
                !this.model.id &&
                this.list && this.list.filter(o => o.id == app.$data.tmp.testResultId).length == 0
            ) {
                this.load()
            }
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
            jumpToCollectRecord(item) {
                this.tabs.decideId = item.id;
                this.tabs.startTime = this.model.startTime;
                this.tabs.type = 'CollectResult';
            },
            fieldAc() {
                return {
                    keyName: 'id',
                        titleName: 'cnName',
                    minWord: 1,
                    loadData: (filter, cb) => {
                    $.ajax({
                        url: 'mnt/fieldPage',
                        data: {page: 1, pageSize: 5, kw: filter},
                        success: (res) => {
                            this.isLoading = false;
                            if (res.code === '00') {
                                cb(res.data.list.map((r) => {
                                    return {id: r.id, cnName: r.cnName}
                                }))
                            } else this.$Notice.error(res.desc)
                        },
                    });
                }
                }
            },
            addAttrFilter() {
                this.model.attrFilters.push({fieldId: null, fieldAc: this.fieldAc(), op: '=', value: null})
            },
            delAttrFilter(item) {
                this.model.attrFilters.splice(this.model.attrFilters.indexOf(item), 1);
            },
            jumpToDecision(item) {
                this.tabs.showId = item.decisionId;
                this.tabs.type = 'DecisionConfig';
            },
            showDetail(item, event) {
                // h-table-tr-hovered
                //console.log('===========', event)
                this.$Modal({
                    draggable: false, hasCloseIcon: true, fullScreen: false, transparent: false, closeOnMask: true,
                    type: 'drawer-right',
                    component: {
                        vue: detail,
                        datas: {item: item}
                    },
                    width: document.body.clientWidth - 250,
                    events: {
                        // reload: () => {
                        //     this.load()
                        // }
                    }
                })
            },
            formatType(v) {
                for (let type of types) {
                    if (type.key == v) return type.title
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
                let data = $.extend(true, {page: page.page || 1}, this.model);
                if (data.attrFilters) {
                    data.attrConditions = JSON.stringify(data.attrFilters.map(item => {delete item.fieldAc; return item}).filter(o => o.fieldId != null));
                    delete data.attrFilters;
                }
                $.ajax({
                    url: 'mnt/decisionResultPage',
                    data: data,
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
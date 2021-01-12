<template>
    <div class="h-panel">
        <div class="h-panel-bar">
            <h-select v-model="model.decision" :datas="types" placeholder="所有" style="width: 70px; float: left" @change="load"></h-select>
            <h-autocomplete v-model="model.decisionId" :option="decisions" style="float:left; width: 150px" @change="load" placeholder="决策名"></h-autocomplete>
            <input type="text" v-model="model.id" placeholder="流水id(精确匹配)" style="width: 250px" @keyup.enter="load"/>
<!--            <input type="text" v-model="model.idNum" placeholder="身份证(精确匹配)" style="width: 155px" @keyup.enter="load"/>-->
            <input type="number" v-model="model.spend" placeholder=">=耗时(ms)" style="width: 100px" @keyup.enter="load"/>
            <input type="text" v-model="model.exception" placeholder="异常信息" @keyup.enter="load"/>
<!--            <input type="text" v-model="model.attrs" placeholder="属性关键字" @keyup.enter="load"/>-->
<!--            <input type="text" v-model="model.rules" placeholder="规则关键字" @keyup.enter="load"/>-->
            <button class="h-btn h-btn-primary float-right" @click="load"><i class="h-icon-search"></i><span>查询</span></button>
        </div>
        <div class="h-panel-body">
            <h-table ref="table" :datas="list" stripe select-when-click-tr :loading="loading" @trdblclick="trdblclick">
                <h-tableitem title="决策" align="center" :width="150">
                    <template slot-scope="{data}">
                        <a v-if="data.decisionName" href="javascript:void(0)" @click="jumpToDecision(data)">{{data.decisionName}}</a>
                        <span v-else>{{data.decisionId}}</span>
                    </template>
                </h-tableitem>
                <h-tableitem title="流水id" prop="id" align="center"></h-tableitem>
<!--                <h-tableitem title="身份证" prop="idNum" align="center" :width="140"></h-tableitem>-->
                <h-tableitem title="决策" prop="decision" align="center" :format="formatType" :width="70"></h-tableitem>
                <h-tableitem title="决策时间" align="center" :width="140">
                    <template slot-scope="{data}">
                        <date-item :time="data.occurTime" />
                    </template>
                </h-tableitem>
                <h-tableitem title="耗时(ms)" prop="spend" align="center" :width="70"></h-tableitem>
                <h-tableitem title="异常信息" prop="exception" align="center"></h-tableitem>
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
    const detail = {
        props: ['item'],
        template:`
            <div style="max-height: 800px">
                <header class="h-modal-header text-center">{{title}}</header>
                <h-layout>
                    <h-header>
                        <h-form readonly>
                            <h-formitem label="入参">{{item.input}}</h-formitem>
                        </h-form>
                    </h-header>
                    <h-layout>
                        <h-sider style="flex: none; max-width: 60%; width: auto">
                            <div class="h-panel" style="max-height: 680px">
                                <div class="h-panel-bar">执行规则集
                                    <div class="h-panel-right">
                                        <Search placeholder="规则名" v-width="200" @search="ruleFilter" v-model="ruleKw"></Search>
                                        <i class="h-split"></i><button class="h-btn h-btn-green h-btn-m" @click="ruleFilter">查询</button>
                                    </div>
                                </div>
                                <div class="h-panel-body">
                                    <h-table :datas="rules" stripe select-when-click-tr border :height="520">
                                        <h-tableitem title="规则属性" :width="220" align="left">
                                            <template slot-scope="{data}">
                                                <h-form readonly>
                                                    <h-formitem v-for="(v,k) in data.attrs" :key="k" :label="k">{{v}}</h-formitem>
                                                </h-form>
                                            </template>
                                        </h-tableitem>
                                        <h-tableitem title="决策" prop="decision" align="center" :width="80" :format="formatType"></h-tableitem>
                                        <h-tableitem title="数据" align="left">
                                            <template slot-scope="{data}">
                                                <h-form readonly :labelWidth="170">
                                                    <h-formitem v-for="item in data.data" :key="item.enName" :label="item.cnName ? item.cnName : item.enName">{{item.value}}</h-formitem>
                                                </h-form>
                                            </template>
                                        </h-tableitem>
                                        <div slot="empty">无规则</div>
                                    </h-table>
                                </div>
                            </div>
                        </h-sider>
                        <h-content>
                            <div class="h-panel" style="max-height: 680px">
                                <div class="h-panel-bar">属性结果集
                                    <div class="h-panel-right">
                                        <Search placeholder="属性名" v-width="200" @search="attrFilter" v-model="attrKw"></Search>
                                        <i class="h-split"></i><button class="h-btn h-btn-green h-btn-m" @click="attrFilter">查询</button>
                                    </div>
                                </div>
                                <div class="h-panel-body">
                                    <h-table :datas="attrs" stripe select-when-click-tr border :height="520">
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
                    <h-footer v-if="item.dataCollectResult">
                        <h-form readonly>
                            <h-formitem label="数据收集">{{item.dataCollectResult}}</h-formitem>
                        </h-form>
                    </h-footer>
                </h-layout>
            </div>
        `,
        data() {
            return {
                ruleKw: null,
                attrKw: null,
                attrs: this.item.attrs,
                rules: this.item.rules,
            }
        },
        computed: {
            title: function () {
                return this.item.decisionName + ' ' + this.item.id + ' ' + this.formatType(this.item.decision);
            }
        },
        methods: {
            formatType(v) {
                for (let type of types) {
                    if (type.key == v) return type.title
                }
                return v
            },
            ruleFilter() {
                if (this.ruleKw) {
                    this.rules = this.item.rules.filter((item, index, arr) => {
                        return item.attrs.规则名 ? item.attrs.规则名.indexOf(this.ruleKw) >= 0 : false
                    });
                } else this.rules = this.item.rules;
            },
            attrFilter() {
                if (this.attrKw) {
                    this.attrs = this.item.attrs.filter((item, index, arr) => {
                        return item.enName ? item.enName.toLowerCase().indexOf(this.attrKw.toLowerCase()) >= 0 : (item.cnName ? item.cnName.toLowerCase().indexOf(this.attrKw.toLowerCase()) >= 0 : false)
                    })
                } else this.attrs = this.item.attrs;
            }
        }
    };
    module.exports = {
        props: ['tabs', 'menu'],
        data() {
            return {
                types: types,
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
                model: {},
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
        methods: {
            jumpToDecision(item) {
                this.tabs.showId = item.decisionId;
                this.tabs.type = 'DecisionConfig';
            },
            trdblclick(item) {
                this.$Modal({
                    middle: false, draggable: false,
                    type: 'drawer-right',
                    component: {
                        vue: detail,
                        datas: {item: item}
                    },
                    width: 1200,
                    hasCloseIcon: true, fullScreen: false, transparent: false, closeOnMask: true,
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
                $.ajax({
                    url: 'mnt/decisionResultPage',
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
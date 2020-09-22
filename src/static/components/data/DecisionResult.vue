<style>
    .inlinetb {
        float: left;
    }
</style>
<template>
    <div class="h-panel">
        <div class="h-panel-bar">
            <h-select v-model="model.decision" :datas="types" placeholder="所有" style="width: 90px; float: left" @change="load"></h-select>
<!--            <h-select v-model="model.decisionId" :datas="decisions" placeholder="所有" style="width: 150px; float: left" @change="load"></h-select>-->
            <h-autocomplete v-model="model.decisionId" :option="decisions" style="float:left; width: 150px" @change="load" placeholder="决策名"></h-autocomplete>
            <input type="text" v-model="model.id" placeholder="流水id(精确匹配)" style="width: 250px" @keyup.enter="load"/>
            <input type="text" v-model="model.idNum" placeholder="身份证(精确匹配)" @keyup.enter="load"/>
            <input type="text" v-model="model.exception" placeholder="异常信息" @keyup.enter="load"/>
            <input type="number" v-model="model.spend" placeholder="大于耗时(ms)" style="width: 110px" @keyup.enter="load"/>
            <input type="text" v-model="model.attrs" placeholder="属性关键字" @keyup.enter="load"/>
            <input type="text" v-model="model.rules" placeholder="规则关键字" @keyup.enter="load"/>
            <button class="h-btn h-btn-primary float-right" @click="load"><i class="h-icon-search"></i><span>查询</span></button>
        </div>
        <div class="h-panel-body">
            <h-table ref="table" :datas="list" stripe select-when-click-tr :loading="loading" @trdblclick="trdblclick">
                <h-tableitem title="决策名" prop="decisionName" align="center" :width="150"></h-tableitem>
                <h-tableitem title="流水id" prop="id" align="center"></h-tableitem>
                <h-tableitem title="身份证" prop="idNum" align="center" :width="140"></h-tableitem>
                <h-tableitem title="决策" prop="decision" align="center" :format="formatType" :width="70"></h-tableitem>
                <h-tableitem title="决策时间" prop="occurTime" align="center" :format="formatDate" :width="140"></h-tableitem>
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
    loadJs('moment');
    const types = [
        { title: '拒绝', key: 'Reject'},
        { title: '通过', key: 'Accept'},
        { title: '人工', key: 'Review'},
    ];
    const detail = {
        props: ['item'],
        template:`
            <div>
                <h-table :datas="rules" stripe select-when-click-tr>
                    <h-tableitem title="属性" prop="attrs" align="center"></h-tableitem>
                    <h-tableitem title="决策" prop="decision" align="center" :width="100"></h-tableitem>
                    <h-tableitem title="数据" prop="data" align="center"></h-tableitem>
                    <div slot="empty">无属性</div>
                </h-table>
<!--                <h-table :datas="attrs" stripe select-when-click-tr className="inlinetb">-->
<!--                    <h-tableitem title="属性名" prop="name" align="center"></h-tableitem>-->
<!--                    <h-tableitem title="属性值" prop="value" align="center"></h-tableitem>-->
<!--                    <div slot="empty">无属性</div>-->
<!--                </h-table>-->
            </div>
        `,
        data() {
            let jo = JSON.parse(this.item.attrs);
            let attrs = [];
            for (let k in jo) {
                attrs.push({name: k, value: jo[k]})
            }

            // let rules = [];
            // let keys = new Set();
            // JSON.parse(this.item.rules).forEach((v, i) => {
            //     let o = {decision: null};
            //     o = v.decision;
            //     $.extend(o, v.attrs);
            //     //o['data'] = JSON.stringify(v.data);
            //     rules.push(o);
            //     for (let k in o) keys.add(k)
            // });
            // console.log(rules);
            // console.log(keys);
            return {
                attrs: attrs,
                rules: JSON.parse(this.item.rules),
                // keys: keys
            }
        }
    };
    module.exports = {
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
                            data: {page: 1, pageSize: 5, kw: filter},
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
        methods: {
            trdblclick(item) {
                this.$Modal({
                    middle: true, draggable: false,
                    type: 'drawer-right',
                    component: {
                        vue: detail,
                        datas: {item: item}
                    },
                    width: 800,
                    hasCloseIcon: true, fullScreen: false, middle: false, transparent: false, closeOnMask: true,
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
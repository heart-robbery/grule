<template>
    <div class="h-panel">
        <div class="h-panel-bar">
<!--            <span class="h-panel-title">属性集</span>-->
            <!--            <span v-color:gray v-font="13">说明~~</span>-->
            &nbsp;&nbsp;
            <h-button v-if="sUser.permissions.find((e) => e == 'field-add') == 'field-add'" @click="showAddPop"><i class="h-icon-plus"></i></h-button>
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
                <h-tableitem title="类型" prop="type" align="center" :format="formatType"></h-tableitem>
                <h-tableitem title="更新时间" prop="updateTime" align="center" :format="formatDate"></h-tableitem>
                <!--                    <h-tableitem title="创建时间" prop="createTime" align="center"></h-tableitem>-->
                <h-tableitem title="描述" prop="comment" align="center"></h-tableitem>
                <h-tableitem title="值函数名" align="center">
                    <template slot-scope="{data}">
                        <a v-if="data.dataCollectorName" href="javascript:void(0)" @click="jumpToDataCollector(data)">{{data.dataCollectorName}}</a>
                        <span v-else>{{data.dataCollector}}</span>
                    </template>
                </h-tableitem>
                <h-tableitem v-if="sUser.permissions.find((e) => e == 'field-update' || e == 'field-del')" title="操作" align="center" :width="100">
                    <template slot-scope="{data}">
                        <span v-if="sUser.permissions.find((e) => e == 'field-update') == 'field-update'" class="text-hover" @click="showUpdatePop(data)">编辑</span>
                        <span v-if="sUser.permissions.find((e) => e == 'field-del') == 'field-del'" class="text-hover" @click="del(data)">删除</span>
                    </template>
                </h-tableitem>
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
    loadJs('md5');
    const types = [
        { title: '字符串', key: 'Str'},
        { title: '整型', key: 'Int' },
        { title: '小数', key: 'Decimal' },
        { title: '布尔', key: 'Bool'},
    ];
    const addEditPop = { //添加,编辑窗口组件
        template: `
                <div v-width="400" style="padding-top: 10px">
                    <h-form ref="form"
                            :valid-on-change="true"
                            :show-error-tip="true"
                            :label-position="'left'"
                            :label-width="110"
                            :rules="validationRules"
                            :model="model">
                        <h-formitem label="英文名" prop="enName" icon="h-icon-user">
                            <input type="text" v-model="model.enName" :readonly="field">
                        </h-formitem>
                        <h-formitem label="中文名" icon="h-icon-user" prop="cnName">
                            <input type="text" v-model="model.cnName">
                        </h-formitem>
                        <h-formitem label="类型" icon="h-icon-complete" prop="type">
                            <h-select v-model="model.type" :datas="types"></h-select>
                        </h-formitem>
                        <h-formitem label="描述" icon="h-icon-complete" prop="comment">
                            <textarea v-model="model.comment" />
                        </h-formitem>
                        <h-formitem label="值函数" icon="h-icon-complete" prop="dataCollector">
                            <h-autocomplete ref="ac" v-model="model.dataCollector" :show="model.dataCollectorName" :option="param"></h-autocomplete>
                        </h-formitem>
                        <h-formitem>
                                <h-button v-if="model.id" color="primary" :loading="isLoading" @click="update">提交</h-button>
                                <h-button v-else color="primary" :loading="isLoading" @click="add">提交</h-button>
                                &nbsp;&nbsp;&nbsp;
                                <h-button v-if="model.id" @click="model = {type: 'Str'}">清除</h-button>
                                <h-button v-else @click="model = {type: 'Str'}">重置</h-button>
                            </h-formitem>
                    </h-form>
                </div>
                `,
            props: ['field'],
            data() {
                return {
                    isLoading: false,
                    model: this.field ? $.extend({}, this.field) : {type: 'Str'},
                    validationRules: {
                        required: ['enName', 'cnName']
                    },
                    types: types,
                    param: {
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
                                            return {cnName: r.cnName, enName: r.enName}
                                        }))
                                    } else this.$Notice.error(res.desc)
                                },
                            });
                        }
                    }
                }
            },
            methods: {
                update() {
                    this.isLoading = true;
                    $.ajax({
                        url: 'mnt/updateField',
                        type: 'post',
                        data: this.model,
                        success: (res) => {
                            this.isLoading = false;
                            if (res.code == '00') {
                                this.$emit('close');
                                this.$Message.success(`更新字段: ${this.model.cnName} 成功`);
                                $.extend(this.field, this.model);
                            } else this.$Notice.error(res.desc)
                        },
                        error: () => this.isLoading = false
                    })
                },
                add() {
                    this.isLoading = true;
                    $.ajax({
                        url: 'mnt/addField',
                        type: 'post',
                        data: this.model,
                        success: (res) => {
                            this.isLoading = false;
                            if (res.code == '00') {
                                this.$emit('close');
                                this.$Message.success(`添加字段: ${this.model.cnName} 成功`);
                                this.$emit('reload');
                            } else this.$Notice.error(res.desc)
                        },
                        error: () => this.isLoading = false
                    })
                },
            }
    };

    module.exports = {
        props: ['tabs'],
        data() {
            return {
                sUser: app.$data.user,
                kw: '',
                loading: false,
                page: 1, totalRow: 0, pageSize: 10, list: []
            }
        },
        mounted() {
            this.load()
        },
        components: {
            'add-pop': addEditPop
        },
        methods: {
            jumpToDataCollector(item) {
                this.tabs.showId = item.dataCollector;
                this.tabs.type = 'DataCollectorConfig';
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
            showAddPop() {
                this.$Modal({
                    title: '添加字段', middle: true, draggable: true,
                    component: {
                        vue: addEditPop,
                        datas: {}
                    },
                    width: 500,
                    hasCloseIcon: true, fullScreen: false, middle: false, transparent: false,
                    events: {
                        reload: () => {
                            this.load()
                        }
                    }
                })
            },
            showUpdatePop(field) {
                this.$Modal({
                    title: `更新字段: ${field.cnName}`, middle: true, draggable: true,
                    component: {
                        vue: addEditPop,
                        datas: {field: field}
                    },
                    width: 500,
                    hasCloseIcon: true, fullScreen: false, middle: false, transparent: false
                })
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
                this.page = 1;
                this.pageSize = 10;
                this.totalRow = 0;
                this.list = [];
                $.ajax({
                    url: 'mnt/fieldPage',
                    data: {page: page.page || 1, kw: this.kw},
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
<template>
    <div class="h-panel">
        <div class="h-panel-bar">
            <h-button v-if="sUser.permissionIds.find((e) => e == 'field-add')" @click="showAddPop"><i class="h-icon-plus"></i></h-button>
            <input type="text" v-model="model.kw" placeholder="关键词" style="width: 250px" @keyup.enter="load"/>
            <h-autocomplete v-model="model.collector" :option="collectorOpt" style="float:left; width: 180px" @change="load" placeholder="收集器"></h-autocomplete>
            <h-autocomplete v-model="model.decision" :option="decisionOpt" style="float:left; width: 180px" @change="load" placeholder="决策"></h-autocomplete>
            <div class="h-panel-right">
                <button class="h-btn h-btn-primary float-right" @click="load"><i class="h-icon-search"></i><span>搜索</span></button>
            </div>
        </div>
        <div class="h-panel-body">
            <h-table ref="table" :datas="list" stripe select-when-click-tr :loading="loading" border>
                <h-tableitem title="英文名" align="center">
                  <template slot-scope="{data}"><span :title="data.enName">{{data.enName}}</span></template>
                </h-tableitem>
                <h-tableitem title="中文名" align="center">
                  <template slot-scope="{data}"><span :title="data.cnName">{{data.cnName}}</span></template>
                </h-tableitem>
                <h-tableitem title="类型" prop="type" align="center" :format="formatType" :width="70"></h-tableitem>
                <h-tableitem title="更新时间" align="center">
                    <template slot-scope="{data}"><date-item :time="data.updateTime" /></template>
                </h-tableitem>
                <!--                    <h-tableitem title="创建时间" prop="createTime" align="center"></h-tableitem>-->
                <h-tableitem title="收集器" align="center">
                    <template slot-scope="{data}">
                        <div v-if="data.collectorOptions" v-for="opt in data.collectorOptions" :key="opt.collectorId">
                            <a v-if="opt.collectorName" href="javascript:void(0)" @click="jumpToDataCollector(opt)">{{opt.collectorName}}</a>
                            <span v-else>{{opt.collectorId}}</span>
                        </div>
                    </template>
                </h-tableitem>
                <h-tableitem title="描述" prop="comment" align="center"></h-tableitem>
                <h-tableitem v-if="sUser.permissionIds.find((e) => e == 'field-update' || e == 'field-del')" title="操作" align="center" :width="90">
                    <template slot-scope="{data}">
                        <span v-if="sUser.permissionIds.find((e) => e == 'field-update') == 'field-update'" class="text-hover" @click="showUpdatePop(data)">编辑</span>
                        <span v-if="sUser.permissionIds.find((e) => e == 'field-del') == 'field-del'" class="text-hover" @click="del(data)">删除</span>
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
    loadJs('md5');
    const types = [
        { title: '字符串', key: 'Str'},
        { title: '整型', key: 'Int' },
        { title: '小数', key: 'Decimal' },
        { title: '布尔', key: 'Bool'},
    ];
    const addEditPop = { //添加,编辑窗口组件
        template: `
                <div v-width="600" style="padding-top: 10px">
                    <h-form ref="form"
                            :valid-on-change="true"
                            :show-error-tip="true"
                            :label-position="'left'"
                            :label-width="110"
                            :rules="validationRules"
                            :model="model">
                        <h-formitem label="英文名" prop="enName" icon="h-icon-user">
                            <input type="text" v-model="model.enName">
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
<!--                            <h-autocomplete v-model="model.dataCollector" :show="model.dataCollectorName" :option="param"></h-autocomplete>-->
                            <div v-for="(opt, index) in model.collectorOptions" :key="opt">
                                <h-autocomplete v-model="opt.collectorId" :show="opt.collectorName" :option="opt.ac"></h-autocomplete>
                                <ace-groovy v-model="opt.chooseFn" style="height: 70px"></ace-groovy>
                                <i v-if="model.collectorOptions.length == (index + 1)" class="h-icon-plus" @click="addCollectorOpt"></i>
                                <i class="h-icon-minus" @click="delCollectorOpt(opt)"></i>
                            </div>
                        </h-formitem>
                        <h-formitem>
                            <h-button v-if="model.id" color="primary" :loading="isLoading" @click="update">提交</h-button>
                            <h-button v-else color="primary" :loading="isLoading" @click="add">提交</h-button>
                        </h-formitem>
                    </h-form>
                </div>
                `,
            props: ['field'],
            data() {
                if (this.field) {
                    if (this.field.collectorOptions && this.field.collectorOptions.length > 0) {
                        this.field.collectorOptions.forEach((item) => {
                            item.ac = this.collectorAc();
                        })
                    } else {
                        this.field.collectorOptions = [{collectorId: null, chooseFn: 'true', ac: this.collectorAc()}];
                    }
                }
                return {
                    isLoading: false,
                    model: this.field ? $.extend({}, this.field) : {type: 'Str', collectorOptions: [{collectorId: null, chooseFn: 'true', ac: this.collectorAc()}]},
                    validationRules: {
                        required: ['enName', 'cnName', 'type', 'collectorOptions.collectorId']
                    },
                    types: types,
                    decisionAc: {
                        keyName: 'id',
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
                                            return {id: r.id, name: r.name}
                                        }))
                                    } else this.$Notice.error(res.desc)
                                },
                            });
                        }
                    }
                }
            },
            mounted() {
                document.onkeyup = (e) => {
                    if (e.code === 'Escape') this.$emit('close');
                }
            },
            methods: {
                addCollectorOpt() {
                    this.model.collectorOptions.push({collectorId: null, chooseFn: 'true', ac: this.collectorAc()})
                },
                delCollectorOpt(opt) {
                    this.model.collectorOptions.splice(this.model.collectorOptions.indexOf(opt), 1)
                },
                collectorAc() {
                    return {
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
                    }
                },
                update() {
                    let data = $.extend({}, this.model)
                    data.collectorOptions = [];
                    if (this.model.collectorOptions && this.model.collectorOptions.length > 0) {
                        this.model.collectorOptions.forEach((value) => {
                            if (value.collectorId) {
                                data.collectorOptions.push({collectorId: value.collectorId, chooseFn: value.chooseFn})
                            }
                        })
                    }
                    if (data.collectorOptions && data.collectorOptions.length > 0) {
                        data.collectorOptions = JSON.stringify(data.collectorOptions);
                    }
                    this.isLoading = true;
                    $.ajax({
                        url: 'mnt/updateField',
                        type: 'post',
                        data: data,
                        success: (res) => {
                            this.isLoading = false;
                            if (res.code === '00') {
                                this.$emit('close');
                                this.$Message.success(`更新字段: ${this.model.cnName} 成功`);
                                //$.extend(this.field, this.model);
                                this.$emit('reload');
                            } else this.$Message.error(res.desc)
                        },
                        error: () => this.isLoading = false
                    })
                },
                add() {
                    let data = $.extend({}, this.model)
                    data.collectorOptions = [];
                    if (this.model.collectorOptions && this.model.collectorOptions.length > 0) {
                        this.model.collectorOptions.forEach((value) => {
                            if (value.collectorId) {
                                data.collectorOptions.push({collectorId: value.collectorId, chooseFn: value.chooseFn})
                            }
                        })
                    }
                    if (data.collectorOptions && data.collectorOptions.length > 0) {
                        data.collectorOptions = JSON.stringify(data.collectorOptions);
                    }
                    this.isLoading = true;
                    $.ajax({
                        url: 'mnt/addField',
                        type: 'post',
                        data: data,
                        success: (res) => {
                            this.isLoading = false;
                            if (res.code === '00') {
                                this.$emit('close');
                                this.$Message.success(`添加字段: ${this.model.cnName} 成功`);
                                this.$emit('reload');
                            } else this.$Message.error(res.desc)
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
                model: {kw: null, collector: null},
                collectorOpt: {
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
                decisionOpt: {
                    keyName: 'id',
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
                                        return {id: r.id, name: r.name}
                                    }))
                                } else this.$Message.error(res.desc)
                            },
                        });
                    }
                },
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
                this.tabs.showId = item.collectorId;
                this.tabs.type = 'DataCollectorConfig';
            },
            jumpToDecision(item) {
                this.tabs.showId = item.decision;
                this.tabs.type = 'DecisionConfig';
            },
            formatType(v) {
                for (let type of types) {
                    if (type.key == v) return type.title
                }
                return v
            },
            showAddPop() {
                this.$Modal({
                    title: '添加字段', draggable: true,
                    component: {
                        vue: addEditPop,
                        datas: {}
                    },
                    width: 680,
                    hasCloseIcon: true, fullScreen: false, middle: true, transparent: false, closeOnMask: false,
                    events: {
                        reload: () => {
                            this.load()
                        }
                    }
                })
            },
            showUpdatePop(field) {
                this.$Modal({
                    title: `更新字段: ${field.cnName}`, draggable: true,
                    component: {
                        vue: addEditPop,
                        datas: {field: field}
                    },
                    width: 680,
                    hasCloseIcon: true, fullScreen: false, middle: true, transparent: false,
                    events: {
                        reload: () => {
                            this.load()
                        }
                    }
                })
            },
            del(field) {
                this.$Confirm(`删除字段: ${field.cnName}`, '确定删除?').then(() => {
                    this.$Message(`删除字段: ${field.cnName}`);
                    $.ajax({
                        url: 'mnt/delField/' + field.id,
                        success: (res) => {
                            if (res.code === '00') {
                                this.$Message.success(`删除字段: ${field.cnName} 成功`);
                                this.load();
                            } else this.$Message.error(res.desc)
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
                    data: $.extend({page: page.page || 1}, this.model),
                    success: (res) => {
                        this.loading = false;
                        if (res.code === '00') {
                            this.page = res.data.page;
                            this.pageSize = res.data.pageSize;
                            this.totalRow = res.data.totalRow;
                            this.list = res.data.list;
                        } else this.$Message.error(res.desc)
                    },
                    error: () => this.loading = false
                })
            }
        }
    }
</script>
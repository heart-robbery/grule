<template>
    <div class="h-panel">
        <div class="h-panel-bar">
<!--            <span class="h-panel-title">数据集</span>-->
            <!--            <span v-color:gray v-font="13">说明~~</span>-->
            <h-button v-if="sUser.permissionIds.find((e) => e == 'dataCollector-add') == 'dataCollector-add'" @click="showAddPop"><i class="h-icon-plus"></i></h-button>
            <h-select v-model="model.type" :datas="types" placeholder="所有" style="width: 70px; float: left" @change="load"></h-select>
            <input type="text" v-model="model.kw" placeholder="关键词" @keyup.enter="load"/>
            <div class="h-panel-right">
<!--                <h-search placeholder="查询" v-width="200" v-model="model.kw" show-search-button search-text="搜索" @search="load"></h-search>-->
                <!--                <i class="h-split"></i>-->
                <button class="h-btn h-btn-green h-btn-m" @click="load">查询</button>
            </div>
        </div>
        <div class="h-panel-body">
            <h-table ref="table" :datas="list" stripe select-when-click-tr :loading="loading">
                <h-tableitem title="英文名" prop="enName" align="center"></h-tableitem>
                <h-tableitem title="中文名" prop="cnName" align="center"></h-tableitem>
                <h-tableitem title="类型" prop="type" align="center" :format="formatType"></h-tableitem>
                <h-tableitem title="更新时间" align="center">
                    <template slot-scope="{data}">
                        <date-item :time="data.updateTime" />
                    </template>
                </h-tableitem>
                <h-tableitem title="描述" prop="comment" align="center"></h-tableitem>
                <h-tableitem title="状态" align="center" :width="80">
                    <template slot-scope="{data}">
                        <h-switch v-model="data.enabled" @change="enableSwitch(data)" small>
                            <span slot="open">可用</span>
                            <span slot="close">禁用</span>
                        </h-switch>
                    </template>
                </h-tableitem>
                <h-tableitem v-if="sUser.permissionIds.find((e) => e == 'dataCollector-update' || e == 'dataCollector-del')" title="操作" align="center" :width="100">
                    <template slot-scope="{data}">
                        <span v-if="sUser.permissionIds.find((e) => e == 'dataCollector-update') == 'dataCollector-update'" class="text-hover" @click="showUpdatePop(data)">编辑</span>
                        &nbsp;
                        <span v-if="sUser.permissionIds.find((e) => e == 'dataCollector-del') == 'dataCollector-del'" class="text-hover" @click="del(data)">删除</span>
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
    const types = [
        { title: '接口', key: 'http'},
        { title: '脚本', key: 'script' },
        { title: 'SQL', key: 'sql' },
    ];
    const addEditPop = { //添加,编辑窗口组件
        template: `
                <div v-width="780" style="padding-top: 10px">
                    <h-form ref="form"
                            :valid-on-change="true"
                            :show-error-tip="true"
                            :label-position="'left'"
                            :label-width="110"
                            mode="twocolumn"
                            :rules="validationRules"
                            :model="model">
                        <h-formitem label="英文名" icon="h-icon-complete" prop="enName">
                            <input type="text" v-model="model.enName" :readonly="collector"/>
                        </h-formitem>
                        <h-formitem label="中文名" icon="h-icon-complete" prop="cnName">
                            <input type="text" v-model="model.cnName" />
                        </h-formitem>
                        <h-formitem label="类型" icon="h-icon-complete" prop="type">
                            <h-select v-if="model.id" v-model="model.type" :datas="types" disabled></h-select>
                            <h-select v-else v-model="model.type" :datas="types" :deletable="false"></h-select>
                        </h-formitem>
                        <h-formitem v-if="model.type == 'http'" label="方法" icon="h-icon-user" prop="method">
                            <h-select v-model="model.method" :datas="methods"></h-select>
                        </h-formitem>
                        <h-formitem v-if="model.type == 'http'" label="超时(ms)" icon="h-icon-user" prop="timeout">
                            <h-numberinput v-model="model.timeout" :min="1000" :max="600000"></h-numberinput>
                        </h-formitem>
                        <h-formitem v-if="model.type == 'http' && model.method == 'POST'" label="ContentType" icon="h-icon-user" prop="contentType">
                            <h-select v-model="model.contentType" :datas="contentTypes"></h-select>
                        </h-formitem>
                        <h-formitem label="描述" icon="h-icon-complete" prop="comment" single>
                            <textarea type="text" v-model="model.comment" />
                        </h-formitem>
                        <h-formitem v-if="model.type == 'http'" label="接口地址" prop="url" icon="h-icon-user" single>
                            <input type="text" v-model="model.url">
                        </h-formitem>
                        <h-formitem v-if="model.type == 'http' && model.method == 'POST'" label="请求体" icon="h-icon-complete" prop="bodyStr" single>
                            <textarea v-model="model.bodyStr" rows="6"/>
                        </h-formitem>

                        <h-formitem v-if="model.type == 'sql'" label="数据库连接url" prop="url" icon="h-icon-user" single>
                            <input type="text" v-model="model.url">
                        </h-formitem>
                        <h-formitem v-if="model.type == 'sql'" label="最少闲连接数" prop="minIdle" icon="h-icon-user" >
                            <h-numberinput v-model="model.minIdle" :min="0" :max="50" useInt></h-numberinput>
                        </h-formitem>
                        <h-formitem v-if="model.type == 'sql'" label="最大连接数" prop="maxActive" icon="h-icon-user" >
                            <h-numberinput v-model="model.maxActive" :min="1" :max="100" useInt></h-numberinput>
                        </h-formitem>

                        <h-formitem v-if="model.type == 'http'" label="是否成功" icon="h-icon-complete" prop="dataSuccessScript" single>
                            <ace-groovy v-if="model.type == 'http'" v-model="model.dataSuccessScript" height="100px" width="670px" />
                        </h-formitem>
                        <h-formitem v-if="model.type == 'http'" label="结果解析" icon="h-icon-complete" prop="parseScript" single>
                            <ace-groovy v-if="model.type == 'http'" v-model="model.parseScript" height="200px" width="670px" />
                        </h-formitem>
                        <h-formitem v-if="model.type == 'script'" label="值计算函数" icon="h-icon-complete" prop="computeScript" single>
                            <ace-groovy v-if="model.type == 'script'" v-model="model.computeScript" height="350px" width="670px" />
                        </h-formitem>
                        <h-formitem v-if="model.type == 'sql'" label="sql执行脚本" icon="h-icon-complete" prop="sqlScript" single>
                            <ace-groovy v-if="model.type == 'sql'" v-model="model.sqlScript" height="250px" width="670px" />
                        </h-formitem>
                        <h-formitem single>
                                <h-button v-if="model.id" color="primary" :loading="isLoading" @click="update">提交</h-button>
                                <h-button v-else color="primary" :loading="isLoading" @click="add">提交</h-button>
                            </h-formitem>
                    </h-form>
                </div>
                `,
        props: ['collector'],
        data() {
            let defaultModel = {type: 'http', method: 'GET', timeout: 10000, contentType: 'application/x-www-form-urlencoded', minIdle: 1, maxActive: 8, dataSuccessScript:
`{resultStr ->
    JSON.parseObject(resultStr)?['code'] == '0000'
}`
            };
            return {
                isLoading: false, defaultModel: defaultModel,
                model: this.collector ? $.extend({sqlScript: '', computeScript: '', parseScript: '', dataSuccessScript: ''}, this.collector) : defaultModel,
                validationRules: {
                    required: ['enName', 'cnName']
                },
                types: types,
                methods: [
                    { title: 'GET', key: 'GET'},
                    { title: 'POST', key: 'POST'},
                ],
                contentTypes: [
                    { title: 'json', key: 'application/json'},
                    { title: 'form', key: 'application/x-www-form-urlencoded' },
                ],
            }
        },
        methods: {
            update() {
                this.isLoading = true;
                $.ajax({
                    url: 'mnt/updateDataCollector',
                    type: 'post',
                    data: this.model,
                    success: (res) => {
                        this.isLoading = false;
                        if (res.code == '00') {
                            this.$emit('close');
                            this.$Message.success(`更新: ${this.model.cnName} 成功`);
                            $.extend(this.collector, this.model);
                        } else this.$Notice.error(res.desc)
                    },
                    error: () => this.isLoading = false
                })
            },
            add() {
                this.isLoading = true;
                $.ajax({
                    url: 'mnt/addDataCollector',
                    type: 'post',
                    data: this.model,
                    success: (res) => {
                        this.isLoading = false;
                        if (res.code == '00') {
                            this.$emit('close');
                            this.$Message.success(`添加: ${this.model.cnName} 成功`);
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
                types: types,
                model: {},
                sUser: app.$data.user,
                kw: '',
                loading: false,
                page: 1, totalRow: 0, pageSize: 10, list: []
            }
        },
        mounted() {
            this.load()
        },
        activated() {
            if (this.tabs.showId) this.load()
        },
        components: {
            // 'add-pop': addEditPop
        },
        methods: {
            formatType(v) {
                for (let type of types) {
                    if (type.key == v) return type.title
                }
                return v
            },
            showAddPop() {
                this.$Modal({
                    title: '添加数据收集器', draggable: true,
                    component: {
                        vue: addEditPop,
                        datas: {}
                    },
                    width: 850,
                    hasCloseIcon: true, fullScreen: false, middle: false, transparent: false, closeOnMask: false,
                    events: {
                        reload: () => {
                            this.load()
                        }
                    }
                })
            },
            showUpdatePop(collector) {
                this.$Modal({
                    title: `更新: ${collector.cnName}`, draggable: true,
                    component: {
                        vue: addEditPop,
                        datas: {collector: collector}
                    },
                    width: 850,
                    hasCloseIcon: true, fullScreen: false, middle: false, transparent: false, closeOnMask: false,
                })
            },
            enableSwitch(item) {
                $.ajax({
                    url: 'mnt/updateDataCollector',
                    type: 'post',
                    data: item,
                    success: (res) => {
                        if (res.code == '00') {
                            this.$Message.success(`${item.enabled ? '启用' : '禁用'}: ${item.cnName} 成功`);
                        } else {
                            this.$Notice.error(res.desc);
                            setTimeout(() => item.enabled = !item.enabled, 200)
                        }
                    },
                    error: () => this.isLoading = false
                })
            },
            del(field) {
                this.$Confirm(`删除收集器: ${field.cnName}`, '确定删除?').then(() => {
                    this.$Message(`删除收集器: ${field.cnName}`);
                    $.ajax({
                        url: 'mnt/delDataCollector/' + field.enName,
                        success: (res) => {
                            if (res.code == '00') {
                                this.$Message.success(`删除收集器: ${field.cnName} 成功`);
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
                    url: 'mnt/dataCollectorPage',
                    data: $.extend({page: page.page || 1, enName: this.tabs.showId}, this.model),
                    success: (res) => {
                        this.tabs.showId = null;
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
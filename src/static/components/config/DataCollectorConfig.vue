<template>
    <div class="h-panel">
        <div class="h-panel-bar">
<!--            <span class="h-panel-title">数据集</span>-->
            <!--            <span v-color:gray v-font="13">说明~~</span>-->
            <h-button v-if="sUser.permissions.find((e) => e == 'dataCollector-add') == 'dataCollector-add'" @click="showAddPop"><i class="h-icon-plus"></i></h-button>
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
                <!--                <h-tableitem title="ID" prop="id" align="center"></h-tableitem>-->
                <h-tableitem title="英文名" prop="enName" align="center"></h-tableitem>
                <h-tableitem title="中文名" prop="cnName" align="center"></h-tableitem>
                <h-tableitem title="类型" prop="type" align="center" :format="formatType"></h-tableitem>
                <h-tableitem title="更新时间" prop="updateTime" align="center" :format="formatDate"></h-tableitem>
                <h-tableitem title="描述" prop="comment" align="center"></h-tableitem>
                <h-tableitem v-if="sUser.permissions.find((e) => e == 'dataCollector-update' || e == 'dataCollector-del')" title="操作" align="center" :width="100">
                    <template slot-scope="{data}">
                        <span v-if="sUser.permissions.find((e) => e == 'dataCollector-update') == 'dataCollector-update'" class="text-hover" @click="showUpdatePop(data)">编辑</span>
                        &nbsp;
                        <span v-if="sUser.permissions.find((e) => e == 'dataCollector-del') == 'dataCollector-del'" class="text-hover" @click="del(data)">删除</span>
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
    loadJs('moment');
    loadJs('ace', () => {
        ace.config.set("basePath", "js/lib");
        loadJs('ace-tools');
        // loadJs('ace-lang-rule');
        // loadJs('ace-snip-rule');
        loadJs('ace-lang-groovy');
        loadJs('ace-snip-groovy');
    });
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

                        <h-formitem v-if="model.type == 'http'" label="解析脚本" icon="h-icon-complete" prop="parseScript" single>
                            <div ref="dslEditor" style="height: 260px; width: 670px"></div>
                        </h-formitem>
                        <h-formitem v-if="model.type == 'script'" label="值计算函数" icon="h-icon-complete" prop="computeScript" single>
                            <div ref="dslEditor" style="height: 380px; width: 670px"></div>
                        </h-formitem>
                        <h-formitem v-if="model.type == 'sql'" label="sql执行脚本" icon="h-icon-complete" prop="sqlScript" single>
                            <div ref="dslEditor" style="height: 260px; width: 670px"></div>
                        </h-formitem>
                        <h-formitem single>
                                <h-button v-if="model.id" color="primary" :loading="isLoading" @click="update">提交</h-button>
                                <h-button v-else color="primary" :loading="isLoading" @click="add">提交</h-button>
                                &nbsp;&nbsp;&nbsp;
                                <h-button v-if="model.id" @click="model = {type: 'http', method: 'GET', contentType: 'application/x-www-form-urlencoded'}">清除</h-button>
                                <h-button v-else @click="model = {type: 'http', method: 'GET', contentType: 'application/x-www-form-urlencoded', minIdle: 1, maxActive: 8}">重置</h-button>
                            </h-formitem>
                    </h-form>
                </div>
                `,
        props: ['collector'],
        data() {
            return {
                isLoading: false,
                model: this.collector ? $.extend({}, this.collector) : {type: 'http', method: 'GET', timeout: 10000, contentType: 'application/x-www-form-urlencoded', minIdle: 1, maxActive: 8},
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
        mounted() {
            this.showEditor()
        },
        watch: {
            'model.type': function () {
                this.showEditor()
            }
        },
        methods: {
            showEditor() {
                // 必须用$nextTick 保证dom节点渲染完成
                this.$nextTick(this.initEditor);
            },
            initEditor() {
                // if (this.editor) {this.editor.destroy()}
                // console.log('this.$refs.dslEditor', this.$refs.dslEditor);
                this.editor = ace.edit(this.$refs.dslEditor);
                // console.log('editor: ', this.editor);
                if (this.model.type == 'http') {
                    if (this.model.parseScript) this.editor.session.setValue(this.model.parseScript);
                } else if (this.model.type == 'script') {
                    if (this.model.computeScript) this.editor.session.setValue(this.model.computeScript);
                } else if (this.model.type == 'sql') {
                    if (this.model.sqlScript) this.editor.session.setValue(this.model.sqlScript);
                }
                this.editor.setOptions({
                    enableBasicAutocompletion: true,
                    enableSnippets: true,
                    enableLiveAutocompletion: true,
                    //fontSize: "50pt",
                });
                this.editor.getSession().setUseSoftTabs(true);
                this.editor.on('change', (e) => {
                    if (this.model.type == 'http') {
                        this.model.parseScript = this.editor.session.getValue();
                    } else if (this.model.type == 'script') {
                        this.model.computeScript = this.editor.session.getValue();
                    } else if (this.model.type == 'sql') {
                        this.model.sqlScript = this.editor.session.getValue();
                    }
                });
                this.editor.session.setMode('ace/mode/groovy');
                this.editor.commands.addCommand({
                    name: 'save',
                    bindKey: {win: 'Ctrl-S', mac: 'Command-S'},
                    exec: (editor) => {
                        if (this.model.type == 'http') {
                            this.model.parseScript = this.editor.session.getValue();
                        } else if (this.model.type == 'script') {
                            this.model.computeScript = this.editor.session.getValue();
                        } else if (this.model.type == 'sql') {
                            this.model.sqlScript = this.editor.session.getValue();
                        }
                        // this.save()
                    },
                    // readOnly: false // 如果不需要使用只读模式，这里设置false
                });
            },
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
            formatDate(v) {
                return moment(v).format('YYYY-MM-DD HH:mm:ss')
            },
            formatType(v) {
                for (let type of types) {
                    if (type.key == v) return type.title
                }
                return v
            },
            showAddPop() {
                this.$Modal({
                    title: '添加数据收集器', middle: true, draggable: true,
                    component: {
                        vue: addEditPop,
                        datas: {}
                    },
                    width: 850,
                    hasCloseIcon: true, fullScreen: false, middle: false, transparent: false,
                    events: {
                        reload: () => {
                            this.load()
                        }
                    }
                })
            },
            showUpdatePop(collector) {
                this.$Modal({
                    title: `更新: ${collector.cnName}`, middle: true, draggable: true,
                    component: {
                        vue: addEditPop,
                        datas: {collector: collector}
                    },
                    width: 850,
                    hasCloseIcon: true, fullScreen: false, middle: false, transparent: false
                })
            },
            del(field) {
                this.$Confirm('确定删除?', `删除: ${field.cnName}`).then(() => {
                    this.$Message(`删除: ${field.cnName}`);
                    $.ajax({
                        url: 'mnt/delDataCollector/' + field.enName,
                        success: (res) => {
                            if (res.code == '00') {
                                this.$Message.success(`删除: ${field.cnName} 成功`);
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
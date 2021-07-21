<template>
    <div class="h-panel">
        <div class="h-panel-bar">
            <h-button v-if="sUser.permissionIds.find((e) => e == 'dataCollector-add') == 'dataCollector-add'" @click="showAddPop"><i class="h-icon-plus"></i></h-button>
            <h-select v-model="model.type" :datas="types" placeholder="所有" style="width: 70px; float: left" @change="load"></h-select>
            <input type="text" v-model="model.kw" placeholder="关键词" @keyup.enter="load"/>
            <div class="h-panel-right">
                <button class="h-btn h-btn-primary float-right" @click="load"><i class="h-icon-search"></i><span>搜索</span></button>
            </div>
        </div>
        <div class="h-panel-body">
            <h-table ref="table" :datas="list" stripe select-when-click-tr :loading="loading" border>
                <h-tableitem title="收集器名" prop="name" align="center"></h-tableitem>
                <h-tableitem title="类型" prop="type" align="center" :format="formatType" :width="70"></h-tableitem>
                <h-tableitem title="更新时间" align="center">
                    <template slot-scope="{data}">
                        <date-item :time="data.updateTime" />
                    </template>
                </h-tableitem>
                <h-tableitem title="描述" prop="comment" align="center"></h-tableitem>
                <h-tableitem title="状态" align="center" :width="70">
                    <template slot-scope="{data}">
                        <h-switch v-if="sUser.permissionIds.find((e) => e == 'dataCollector-update')" v-model="data.enabled" @change="enableSwitch(data)" small>
                            <span slot="open" class="h-icon-check"></span>
                            <span slot="close" class="h-icon-close"></span>
                        </h-switch>
                        <span v-else-if="data.enabled" slot="open" class="h-icon-check"></span>
                        <span v-else slot="open" class="h-icon-close"></span>
                    </template>
                </h-tableitem>
                <h-tableitem title="操作" align="center" :width="110">
                    <template slot-scope="{data}">
                        <span v-if="sUser.permissionIds.find((e) => e == 'dataCollector-update')" class="text-hover" @click="showUpdatePop(data)">编辑</span>
                        &nbsp;
                        <span v-if="sUser.permissionIds.find((e) => e == 'dataCollector-del')" class="text-hover" @click="del(data)">删除</span>
                        &nbsp;
                        <span class="text-hover" @click="showTestPop(data)">测试</span>
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
                        <h-formitem label="收集器名" icon="h-icon-complete" prop="name">
                            <input type="text" v-model="model.name"/>
                        </h-formitem>
                        <h-formitem label="类型" icon="h-icon-complete" prop="type">
                            <h-select v-if="model.id" v-model="model.type" :datas="types" disabled></h-select>
                            <h-select v-else v-model="model.type" :datas="types" :deletable="false"></h-select>
                        </h-formitem>
                        <h-formitem v-if="model.type == 'http'" label="方法" icon="h-icon-user" prop="method">
                            <h-select v-model="model.method" :datas="methods"></h-select>
                        </h-formitem>
                        <h-formitem v-if="model.type == 'http'" label="超时(ms)" icon="h-icon-user" prop="timeout">
                            <h-numberinput v-model="model.timeout" :min="20" :max="600000"></h-numberinput>
                        </h-formitem>
                        <h-formitem label="缓存key" icon="h-icon-user" prop="cacheKey">
                            <input type="text" v-model="model.cacheKey" placeholder="用于判断数据是否需要重复计算" />
                        </h-formitem>
                        <h-formitem label="缓存超时(m)" icon="h-icon-user" prop="timeout">
                            <input type="text" v-model="model.cacheTimeoutFn" />
                        </h-formitem>
                        <h-formitem label="记录结果" icon="h-icon-complete" prop="recordResult">
                          <h-checkbox v-model="model.recordResult" />
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
                            <input type="text" v-model="model.url"/>
                        </h-formitem>
                        <h-formitem v-if="model.type == 'sql'" label="最少闲连接数" prop="minIdle" icon="h-icon-user" >
                            <h-numberinput v-model="model.minIdle" :min="0" :max="50" useInt></h-numberinput>
                        </h-formitem>
                        <h-formitem v-if="model.type == 'sql'" label="最大连接数" prop="maxActive" icon="h-icon-user" >
                            <h-numberinput v-model="model.maxActive" :min="1" :max="50" useInt></h-numberinput>
                        </h-formitem>

                        <h-formitem v-show="model.type == 'http'" label="是否成功" icon="h-icon-complete" prop="dataSuccessScript" single>
                          <ace-groovy v-model="model.dataSuccessScript" style="height: 90px; width: 670px"></ace-groovy>
                        </h-formitem>
                        <h-formitem v-show="model.type == 'http'" label="结果解析" icon="h-icon-complete" prop="parseScript" single>
                          <ace-groovy v-model="model.parseScript" style="height: 190px; width: 670px"></ace-groovy>
                        </h-formitem>
                        <h-formitem v-show="model.type == 'script'" label="值计算脚本" icon="h-icon-complete" prop="computeScript" single>
                          <ace-groovy v-model="model.computeScript" style="height: 300px; width: 670px"></ace-groovy>
                        </h-formitem>
                        <h-formitem v-show="model.type == 'sql'" label="sql执行脚本" icon="h-icon-complete" prop="sqlScript" single>
                          <ace-groovy v-model="model.sqlScript" style="height: 250px; width: 670px"></ace-groovy>
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
            return {
                isLoading: false,
                model: this.collector ? $.extend({sqlScript: '', computeScript: '', parseScript: '', dataSuccessScript: ''}, this.collector): {
                    type: 'http', method: 'GET', timeout: 10000, minIdle: 0, maxActive: 5, recordResult: true, dataSuccessScript:
`{resultStr, respCode -> // 接口返回的字符串, 接口http响应码(如果是缓存则为空)
    return (respCode == null || respCode == 200) && resultStr
}`, parseScript: `{resultStr, respCode -> // 接口返回的字符串, 接口http响应码(如果是缓存则为空)
    return resultStr
}`
                },
                validationRules: {
                    required: ['name', 'type']
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
            document.onkeyup = (e) => {
                if (e.code === 'Escape') this.$emit('close');
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
                        if (res.code === '00') {
                            this.$emit('close');
                            this.$Message.success(`更新: ${this.model.name} 成功`);
                            $.extend(this.collector, this.model);
                        } else this.$Message.error(res.desc)
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
                        if (res.code === '00') {
                            this.$emit('close');
                            this.$Message.success(`添加: ${this.model.name} 成功`);
                            this.$emit('reload');
                        } else this.$Message.error(res.desc)
                    },
                    error: () => this.isLoading = false
                })
            },
        }
    };
    const testPop = { //测试弹框
        props: ['collector'],
        template: `
            <div>
            <h-row :space="10">
                <h-cell>
                    <input type="text" :value="url" placeholder="请求地址" style="width: 100%"/>
                </h-cell>
            </h-row>
            <h-row :space="10" v-for="(param,index) in items">
                <h-cell width="8"><input type="text" v-model="param.code" placeholder="参数名" style="float: left; width: 100%"/></h-cell>
                <h-cell width="12">
                    <input type="text" v-model="param.value" :placeholder="param.name" style="float: left; width: 100%"/>
                </h-cell>
                <h-cell width="2">
                    <i v-if="items.length == (index + 1)" class="h-icon-plus" @click="add"></i>
                    <i class="h-icon-minus" @click="del(param)"></i>
                </h-cell>
            </h-row>
            <h-row>
                <h-cell width="24">
                    <h-button @click="test">测试</h-button>
                </h-cell>
            </h-row>
            <h-row>
                <ace-json v-model="result" height="200px" width="99%" :readonly="true"></ace-json>
            </h-row>
            </div>
        `,
        data() {
            let cacheKey = 'rule.test.' + this.collector.id;
            return {
                url: location.protocol + '//' + location.host + '/mnt/testCollector/' + this.collector.id,
                cacheKey: cacheKey,
                items: (() => {
                    let itemsStr = localStorage.getItem(cacheKey);
                    if (itemsStr) return JSON.parse(itemsStr);
                    return [{name: "参数", value: null}]
                })(),
                result: ''
            }
        },
        mounted() {
            document.onkeyup = (e) => {
                if (e.code === 'Escape') this.$emit('close');
            }
        },
        methods: {
            test() {
                let items = this.items.filter(item => item.code);
                if (items && items.length > 0) {
                    items = items.map((param) => {let o={}; o[param.code] = param.value; return o}).reduce((o1, o2) => {let o = {...o1, ...o2}; return o})
                } else items = null;
                this.result = '';
                $.ajax({
                    url: this.url,
                    data: items,
                    success: (res) => {
                        if (res.code === '00') {
                            this.result = JSON.stringify(res.data, null, 4).trim();
                            this.$Message.success(`测试调用: ${this.collector.name} 成功`);
                            localStorage.setItem(this.cacheKey, JSON.stringify(this.items.map(o => {return {code: o.code, value: o.value}})));
                        } else this.$Message.error(res.desc);
                    }
                })
            },
            add() {
                this.items.push({name: `参数${this.items.length + 1}`, value: null})
            },
            del(param) {
                let index = this.items.indexOf(param);
                this.items.splice(index, 1)
            }
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
                        vue: addEditPop, datas: {}
                    },
                    width: 850,
                    hasCloseIcon: true, fullScreen: false, middle: true, transparent: false, closeOnMask: false,
                    events: {
                        reload: () => {
                            this.load()
                        }
                    }
                })
            },
            showUpdatePop(collector) {
                this.$Modal({
                    title: `更新收集器: ${collector.name}`, draggable: true,
                    component: {
                        vue: addEditPop,
                        datas: {collector: collector}
                    },
                    width: 850,
                    hasCloseIcon: true, fullScreen: false, middle: true, transparent: false, closeOnMask: false,
                })
            },
            showTestPop(item) {
                this.$Modal({
                    title: `测试收集器: ${item.name}`, draggable: true,
                    component: {
                        vue: testPop,
                        datas: {collector: item}
                    },
                    width: 750, closeOnMask: false,
                    hasCloseIcon: true, fullScreen: false, middle: true, transparent: false,
                })
            },
            enableSwitch(item) {
                this.$Message.info(`${item.enabled ? '启用' : '禁用'}: ${item.name} ...`);
                $.ajax({
                    url: 'mnt/enableDataCollector',
                    type: 'post',
                    data: {id: item.id, enabled: item.enabled},
                    success: (res) => {
                        if (res.code === '00') {
                            this.$Message.success(`${item.enabled ? '启用' : '禁用'}: ${item.name} 成功`);
                        } else {
                            this.$Message.error(res.desc);
                            setTimeout(() => item.enabled = !item.enabled, 200)
                        }
                    },
                    error: (xhr, status) => {
                      setTimeout(() => item.enabled = !item.enabled, 200)
                      this.$Message.error(`${status} : ${xhr.responseText}`)
                    }
                })
            },
            del(collector) {
                this.$Confirm(`删除收集器: ${collector.name}`, '确定删除?').then(() => {
                    this.$Message(`删除收集器: ${collector.name}`);
                    $.ajax({
                        url: 'mnt/delDataCollector/' + collector.id,
                        success: (res) => {
                            if (res.code === '00') {
                                this.$Message.success(`删除收集器: ${collector.name} 成功`);
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
                    url: 'mnt/dataCollectorPage',
                    data: $.extend({page: page.page || 1, id: this.tabs.showId}, this.model),
                    success: (res) => {
                        this.tabs.showId = null;
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
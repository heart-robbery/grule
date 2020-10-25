<template>
    <div class="h-panel">
        <div class="h-panel-bar">
            &nbsp;&nbsp;
            <h-button v-if="sUser.permissions.find((e) => e == 'decision-add') == 'decision-add'" @click="add"><i class="h-icon-plus"></i></h-button>
            <input type="text" v-model="model.nameLike" placeholder="决策名" style="width: 250px" @keyup.enter="load"/>
            <div class="h-panel-right">
<!--                <h-search placeholder="查询" v-width="200" v-model="kw" show-search-button search-text="搜索" @search="load"></h-search>-->
                <button class="h-btn h-btn-primary float-right" @click="load"><i class="h-icon-search"></i><span>查询</span></button>
            </div>
        </div>
        <div class="h-panel-body">
            <div>
                <h-collapse v-model="collapse" accordion @change="showEditor">
                    <h-collapseitem v-for="item in decision.list" :key="item.decisionId" :name="item.decisionId">
                        <template slot='title'>
                            {{item.name + '(' + item.decisionId + ')' + (item.comment ? ': ' + item.comment : '')}}
                            &nbsp;&nbsp;&nbsp;
                            <date-item :time="item.updateTime"></date-item>
                            <span class="float-right">
                                <h-button text-color="yellow" :circle="true" @click.stop="showApiPop(item)">API配置</h-button>
                                <h-button text-color="yellow" :circle="true" @click.stop="showTestPop(item)">测试</h-button>
                                <h-button v-if="sUser.permissions.find((e) => e == 'decision-del') == 'decision-del'" text-color="red" :circle="true" icon="h-icon-trash" @click.stop="del(item)">删除</h-button>
                            </span>
                        </template>
                        <div style="height: 650px; width: 100vh">
                            <div v-if="collapse && collapse.length > 0 && collapse[0] == item.decisionId " ref="dslEditor" style="height: 650px; width: 800px"></div>
                        </div>
                    </h-collapseitem>
                </h-collapse>
            </div>
        </div>
        <div v-if="decision.totalRow" class="h-panel-bar">
            <h-pagination ref="pagination" :cur="decision.page" :total="decision.totalRow" :size="decision.pageSize" align="right" @change="load" layout="pager,total"></h-pagination>
        </div>
    </div>
</template>
<script>
    loadJs('ace', () => {
        ace.config.set("basePath", "js/lib");
        loadJs('ace-tools');
        // loadJs('ace-lang-rule');
        // loadJs('ace-snip-rule');
        loadJs('ace-lang-groovy');
        loadJs('ace-snip-groovy');
    });
    const apiConfig = {
        props: ['decision'],
        template: `
            <div class="h-panel">
                <div class="h-panel-bar">
                    <h-button @click="add"><i class="h-icon-plus"></i></h-button>
                </div>
                <div class="h-panel-body">
                    <h-table :datas="apiConfig" stripe select-when-click-tr border :height="480">
                        <h-tableitem title="参数名" align="center" :width="200">
                            <template slot-scope="{data}">
                                <input type="text" v-model="data.code" :readonly="data._readonly"/>
                            </template>
                        </h-tableitem>
                        <h-tableitem title="参数说明" align="center" :width="200">
                            <template slot-scope="{data}">
                                <input type="text" v-model="data.name"/>
                            </template>
                        </h-tableitem>
                        <h-tableitem title="类型" align="center" :width="90">
                            <template slot-scope="{data}">
                                <h-select v-model="data.type" :datas="types" :deletable="false" style="width: 80px" :disabled="data._readonly"></h-select>
                            </template>
                        </h-tableitem>
                        <h-tableitem title="是否必须" align="center" :width="80">
                            <template slot-scope="{data}">
                                <h-switch v-model="data.require" small>
                                    <span slot="open">必须</span>
                                    <span slot="close">性必须</span>
                                </h-switch>
                            </template>
                        </h-tableitem>
                        <h-tableitem title="值" align="center" :width="250">
                            <template slot-scope="{data}">
                                <h-form>
                                    <h-formitem v-if="data.type == 'Str'" label="固定值">
                                        <input type="text" v-model="data.fixValue" :readonly="data._readonly"/>
                                    </h-formitem>
                                    <h-formitem v-if="data.type == 'Str'" label="默认值">
                                        <input type="text" v-model="data.defaultValue" :readonly="data._readonly"/>
                                    </h-formitem>
                                    <h-formitem v-if="data.type == 'Str'" label="枚举值">
                                        <h-taginput v-model="data.enumValues"></h-taginput>
                                    </h-formitem>

                                    <h-formitem v-if="data.type == 'Int'" label="固定值">
                                        <h-numberinput v-model="data.fixValue" useInt></h-numberinput>
                                    </h-formitem>
                                    <h-formitem v-if="data.type == 'Int'" label="默认值">
                                        <h-numberinput v-model="data.defaultValue" useInt></h-numberinput>
                                    </h-formitem>

                                    <h-formitem v-if="data.type == 'Decimal'" label="固定值">
                                        <h-numberinput v-model="data.fixValue"></h-numberinput>
                                    </h-formitem>
                                    <h-formitem v-if="data.type == 'Decimal'" label="默认值">
                                        <h-numberinput v-model="data.defaultValue"></h-numberinput>
                                    </h-formitem>

                                    <h-formitem v-if="data.type == 'Bool'" label="固定值">
                                        <h-switch v-model="data.fixValue" small>
                                            <span slot="open">true</span>
                                            <span slot="close">false</span>
                                        </h-switch>
                                    </h-formitem>
                                    <h-formitem v-if="data.type == 'Bool'" label="默认值">
                                        <h-switch v-model="data.defaultValue" small>
                                            <span slot="open">true</span>
                                            <span slot="close">false</span>
                                        </h-switch>
                                    </h-formitem>
                                </h-form>
                            </template>
                        </h-tableitem>
                        <h-tableitem title="验证" align="center" :width="250">
                            <template slot-scope="{data}">
                            <h-form>
                                <h-formitem v-if="data.type == 'Str'" label="最大长度">
                                    <h-numberinput v-model="data.maxLength"></h-numberinput>
                                </h-formitem>
                                <h-formitem v-if="data.type == 'Str'" label="固定长度">
                                    <h-numberinput v-model="data.fixLength"></h-numberinput>
                                </h-formitem>

                                <h-formitem v-if="data.type == 'Int'" label="最小值">
                                    <h-numberinput v-model="data.min" useInt></h-numberinput>
                                </h-formitem>
                                <h-formitem v-if="data.type == 'Int'" label="最大值">
                                    <h-numberinput v-model="data.max" useInt></h-numberinput>
                                </h-formitem>

                                <h-formitem v-if="data.type == 'Decimal'" label="最小值">
                                    <h-numberinput v-model="data.min"></h-numberinput>
                                </h-formitem>
                                <h-formitem v-if="data.type == 'Decimal'" label="最大值">
                                    <h-numberinput v-model="data.max"></h-numberinput>
                                </h-formitem>

                            </h-form>
                        </template>
                        </h-tableitem>
                        <h-tableitem title="操作" align="center" :width="60" fixed="right">
                            <template slot-scope="{data}">
                                <h-button class="h-btn h-btn-s h-btn-red" @click="del(data)"><i class="h-icon-trash"></i></h-button>
                            </template>
                        </h-tableitem>
                    </h-table>
                </div>
                <div class="h-panel-bar">
                </div>
            </div>
        `,
        data() {
            let items = {
                url: location.protocol + '//' + location.host + '/decision?decisionId=' + this.decision.decisionId,
                params: [{name: '参数1', value: null}]
            };
            let cacheKey = 'rule.test.' + this.decision.decisionId;
            let itemsStr = localStorage.getItem(cacheKey);
            if (itemsStr) items = JSON.parse(itemsStr);
            this.decision.apiConfigO = this.decision.apiConfigO ? this.decision.apiConfigO : (this.decision.apiConfig ? JSON.parse(this.decision.apiConfig) : null);
            if (this.decision.apiConfigO) {
                for (let item of this.decision.apiConfigO) {
                    if (item.code == 'decisionId' || item.code == 'async') item._readonly = true
                }
            }
            return {
                cacheKey: cacheKey,
                items: items,
                result: '',
                apiConfig: this.decision.apiConfigO,
                types: [
                    { title: '字符串', key: 'Str'},
                    { title: '整形', key: 'Int' },
                    { title: '布尔', key: 'Bool' },
                    { title: '小数', key: 'Decimal' },
                ]
            }
        },
        methods: {
            test() {
                this.result = '';
                $.ajax({
                    url: this.items.url,
                    data: this.items.params.map((param) => {let o={}; o[param.name] = param.value; return o}).reduce((o1, o2) => {let o = {...o1, ...o2}; return o}),
                    success: (res) => {
                        if (res.code == '00') {
                            this.result = JSON.stringify(res.data, null, 4).trim();
                            this.$Message.success(`测试调用: ${this.decision.decisionId}成功`);
                            localStorage.setItem(this.cacheKey, JSON.stringify(this.items));
                            app.$data.tmp.testResultId = res.data.id;
                        } else this.$Notice.error(res.desc);
                    }
                })
            },
            add() {
                this.decision.apiConfigO.push({name: `参数${this.decision.apiConfigO.length + 1}`, code: null, type: 'Str'})
            },
            del(item) {
                let index = this.decision.apiConfigO.indexOf(item);
                this.decision.apiConfigO.splice(index, 1)
            }
        }
    };
    const testPop = {
        props: ['decision'],
        template: `
            <div>
            <h-row :space="10">
                <h-cell>
                    <input type="text" :value="items.url" placeholder="请求地址" style="width: 100%"/>
                </h-cell>
            </h-row>
            <h-row :space="10" v-for="(param,index) in items.params">
                <h-cell width="8"><input type="text" v-model="param.name" placeholder="参数名" style="float: left; width: 100%"/></h-cell>
                <h-cell width="12"><input type="text" v-model="param.value" placeholder="参数值" style="float: left; width: 100%"/></h-cell>
                <h-cell width="2">
                    <i v-if="items.params.length == (index + 1)" class="h-icon-plus" @click="add"></i>
                    <i class="h-icon-minus" @click="del(param)"></i>
                </h-cell>
            </h-row>
            <h-row>
                <h-cell width="24">
                    <h-button @click="test">测试</h-button>
                </h-cell>
            </h-row>
            <h-row>
                  <pre style="white-space: pre-wrap">
                      {{result}}
                    </pre>
            </h-row>
            </div>
        `,
        data() {
            let items = {
                url: location.protocol + '//' + location.host + '/decision?decisionId=' + this.decision.decisionId,
                params: [{name: '参数1', value: null}]
            };
            let cacheKey = 'rule.test.' + this.decision.decisionId;
            let itemsStr = localStorage.getItem(cacheKey);
            if (itemsStr) items = JSON.parse(itemsStr);
            return {
                cacheKey: cacheKey,
                items: items,
                result: ''
            }
        },
        methods: {
            test() {
                this.result = '';
                $.ajax({
                    url: this.items.url,
                    data: this.items.params.map((param) => {let o={}; o[param.name] = param.value; return o}).reduce((o1, o2) => {let o = {...o1, ...o2}; return o}),
                    success: (res) => {
                        if (res.code == '00') {
                            this.result = JSON.stringify(res.data, null, 4).trim();
                            this.$Message.success(`测试调用: ${this.decision.decisionId}成功`);
                            localStorage.setItem(this.cacheKey, JSON.stringify(this.items))
                            app.$data.tmp.testResultId = res.data.id;
                        } else this.$Notice.error(res.desc);
                    }
                })
            },
            add() {
                this.items.params.push({name: `参数${this.items.params.length + 1}`, value: null})
            },
            del(param) {
                let index = this.items.params.indexOf(param);
                this.items.params.splice(index, 1)
            }
        }
    };
    module.exports = {
        props: ['tabs'],
        data() {
            return {
                sUser: app.$data.user,
                model: {},
                decisionLoading: false,
                decision: {
                    page: 1, pageSize: 2, totalRow: 0, list: []
                },
                collapse: null, curDecision: null,
            };
        },
        mounted: function () {
            this.load()
        },
        activated() {
            if (this.tabs.showId) this.load()
        },
        methods: {
            showApiPop(item) {
                this.$Modal({
                    title: `API配置: ${item.name}`, middle: true, draggable: true,
                    component: {
                        vue: apiConfig,
                        datas: {decision: item}
                    },
                    width: 1000, closeOnMask: false,
                    hasCloseIcon: true, fullScreen: false, middle: false, transparent: false,
                    // events: {
                    //     reload: () => {
                    //         this.load()
                    //     }
                    // }
                })
            },
            showTestPop(item) {
                this.$Modal({
                    title: `测试策略: ${item.name}`, middle: true, draggable: true,
                    component: {
                        vue: testPop,
                        datas: {decision: item}
                    },
                    width: 800, closeOnMask: false,
                    hasCloseIcon: true, fullScreen: false, middle: false, transparent: false,
                    // events: {
                    //     reload: () => {
                    //         this.load()
                    //     }
                    // }
                })
            },
            initEditor() {
                // if (this.editor) {this.editor.destroy()}
                this.editor = ace.edit(this.$refs.dslEditor[0]);
                // console.log('editor: ', this.editor);
                this.editor.session.setValue(this.curDecision.dsl);
                if (this.sUser.permissions.find((e) => e == 'decision-update') != 'decision-update') {
                    this.editor.setReadOnly(true)
                }
                // console.log('$options', this.editor.$options);
                this.editor.setOptions({
                    enableBasicAutocompletion: true,
                    enableSnippets: true,
                    enableLiveAutocompletion: true
                });
                this.editor.on('change', (e) => {
                    //console.log('change: ', e);
                    this.curDecision.dsl = this.editor.session.getValue()
                });
                this.editor.session.setMode('ace/mode/groovy');
                this.editor.commands.addCommand({
                    name: 'save',
                    bindKey: {win: 'Ctrl-S', mac: 'Command-S'},
                    exec: (editor) => {
                        this.curDecision.dsl = editor.session.getValue();
                        this.save()
                    },
                    // readOnly: false // 如果不需要使用只读模式，这里设置false
                });

                let languageTools = ace.require("ace/ext/language_tools");
                // console.log('languageTools', ace.require("ace/ext/language_tools"));
                languageTools.addCompleter({
                    getCompletions: (editor, session, pos, prefix, callback) => {
                        callback(null, [
                            {
                                name : "第一行", //名称
                                value : "身份证号码",//值，这就是匹配我们输入的内容，比如输入s或者select,这一行就会出现在提示框里，可根据自己需求修改，就是你想输入什么显示出北京呢，就改成什么
                                caption: "身",//字幕，下拉提示左侧内容,这也就是我们输入前缀匹配出来的内容，所以这里必须包含我们的前缀
                                meta: "字段-身份证号码", //类型，下拉提示右侧内容
                                type: "local",//可写为keyword
                                score : 1000 // 让它排在最上面，类似权值的概念
                            },
                            {
                                name : "年龄", //名称
                                value : "年龄",//值，这就是匹配我们输入的内容，比如输入s或者select,这一行就会出现在提示框里，可根据自己需求修改，就是你想输入什么显示出北京呢，就改成什么
                                caption: "年",//字幕，下拉提示左侧内容,这也就是我们输入前缀匹配出来的内容，所以这里必须包含我们的前缀
                                meta: "字段-年龄", //类型，下拉提示右侧内容
                                type: "local",//可写为keyword
                                score : 1000 // 让它排在最上面，类似权值的概念
                            }
                        ]);
                    }
                });
            },
            del(item) {
                if (item.id) {
                    this.$Confirm('确定删除?', `删除决策: ${item.decisionId}`).then(() => {
                        this.$Message(`删除决策: ${item.decisionId}`);
                        $.ajax({
                            url: 'mnt/delDecision/' + item.decisionId,
                            success: (res) => {
                                if (res.code == '00') {
                                    this.$Message.success(`删除决策: ${item.decisionId}成功`);
                                    this.load();
                                    localStorage.removeItem('rule.test.' + this.item.decisionId)
                                } else this.$Notice.error(res.desc)
                            }
                        });
                    }).catch(() => {
                        this.$Message.error('取消');
                    });
                } else {
                    let index = this.decision.list.indexOf(item);
                    this.decision.list.splice(index, 1);
                }
            },
            save() {
                let decision = this.curDecision;
                $.ajax({
                    url: 'mnt/setDecision',
                    type: 'post',
                    data: {id: decision.id, dsl: decision.dsl, apiConfig: decision.apiConfigO ? JSON.stringify(decision.apiConfigO) : decision.apiConfig},
                    success: (res) => {
                        if (res.code == '00') {
                            if (decision.id) {
                                $.extend(decision, res.data);
                                this.$Message.success('更新成功: ' + decision.decisionId);
                            } else {
                                this.load();
                                this.$Message.success('新增成功: ' + res.data.decisionId);
                            }
                        } else this.$Notice.error(res.desc)
                    }
                })
            },
            add() {
                let decisionId = 'decision_' + Date.now();
                this.decision.list.unshift({
                    decisionId: decisionId,
                    name: '决策名',
                    apiConfigO: [
                        {
                            "code": "decisionId", "name": "决策id", "type": "Str", "require": true, "fixValue": decisionId
                        },
                        {
                            "code": "async", "name": "是否异步", "type": "Bool", "require": false, "defaultValue": false
                        },
                        {
                            "code": "idNumber", "name": "身份证", "type": "Str", "require": true, "fixLength": 18
                        },
                        {
                            "code": "mobileNo", "name": "手机号", "type": "Str", "require": true, "fixLength": 11
                        },
                        {
                            "code": "name", "name": "姓名", "type": "Str", "require": true, "maxLength": 100
                        }
                    ],
                    dsl:
`// 决策id: 必须唯一
决策id = '${decisionId}'
决策名 = '${decisionId}'
决策描述 = ''

// 返回的调用决策方的结果属性值
返回属性 '身份证号码'

策略定义 {
    策略名 = 'P_预处理'

    条件 { // 执行此策略的条件, false: 不执行, true/不配置默认 执行此策略
        true
    }

    规则定义 {
        规则名 = 'R_参数验证'
        属性定义 '处置代码', 'DC_INPUT_01'

        拒绝 {
            !身份证号码 || !手机号码 || !姓名
        }
    }

    规则定义 {
        规则名 = 'R_属性设值'

        操作 {
            贷前 = true
        }
    }
}
                    `
                })
            },
            showEditor(e) {
                // console.log('showEditor', e);
                if (e && e.length > 0) {
                    this.decision.list.forEach((v, i) => {
                        if (v.decisionId == e[0]) {
                            this.curDecision = v;
                        }
                    });
                    if (window.ace == undefined) {
                        // loadJs('ace', this.initEditor);
                        loadJs('ace', 'ace-lang-tools', this.initEditor);
                    } else {
                        // 必须用$nextTick 保证dom节点渲染完成
                        this.$nextTick(this.initEditor)
                    }
                }
            },
            // tabSwitch(data) {
            //     console.log(data);
            //     this.$emit('tab-switch', 'PolicyConfig', data.decisionId)
            // },
            load(page) {
                if (page == undefined || page == null) page = {page: 1};
                this.decisionLoading = true;
                this.decision = {};
                $.ajax({
                    url: 'mnt/decisionPage',
                    data: $.extend({page: page.page || 1, decisionId: this.tabs.showId}, this.model),
                    success: (res) => {
                        this.tabs.showId = null;
                        this.decisionLoading = false;
                        if (res.code == '00') {
                            this.decision = res.data;
                        } else this.$Notice.error(res.desc)
                    },
                    error: () => this.decisionLoading = false
                })
            }
        }
    };
</script>
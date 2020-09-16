<style scoped>
    .editor-size{
        width: 80vw;
        height: 80vh;
        overflow-y: auto;
    }
</style>
<template>
    <div class="h-panel">
        <div class="h-panel-bar">
<!--            <span class="h-panel-title">决策集</span>-->
<!--            <span v-color:gray v-font="13">说明~~</span>-->
            &nbsp;&nbsp;
            <h-button @click="add"><i class="h-icon-plus"></i></h-button>
            <div class="h-panel-right">
                <h-search placeholder="查询" v-width="200" v-model="kw" show-search-button search-text="搜索" @search="load"></h-search>
<!--                <i class="h-split"></i>-->
<!--                <button class="h-btn h-btn-green h-btn-m" @click="load">查询</button>-->
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
                                <h-button text-color="yellow" :circle="true" @click.stop="showTestPop(item)">测试</h-button>
                                <h-button text-color="red" :circle="true" icon="h-icon-trash" @click.stop="del(item)">删除</h-button>
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
            <h-pagination ref="pagination" :cur="decision.page" :total="decision.totalRow" :size="decision.pageSize"
                          align="right" @change="load" layout="pager,total"></h-pagination>
        </div>
    </div>
</template>
<script>
    const testPop = {
        props: ['decision'],
        template: `
            <div>
                <h-row :space="10">
                    <h-cell>
                        <input type="text" :value="items.url" placeholder="请求地址" style="width: 100%"/>
                    </h-cell>
                </h-row>
                <h-row :space="10" v-for="(param,index) in items.params" :key="param.name">
                    <h-cell width="4"><input type="text" v-model="param.name" placeholder="参数名" style="float: left; width: 100%"/></h-cell>
                    <h-cell width="16"><input type="text" v-model="param.value" placeholder="参数值" style="float: left; width: 100%"/></h-cell>
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
                    {{result}}
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
                            this.result = res.data;
                            this.$Message.success(`测试调用: ${this.decision.decisionId}成功`);
                            localStorage.setItem(this.cacheKey, JSON.stringify(this.items))
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
                decisionLoading: false,
                decision: {
                    page: 1, pageSize: 2, totalRow: 0, list: []
                },
                collapse: null, curDecision: null,
                kw: '',
            };
        },
        mounted: function () {
            this.load()
        },
        methods: {
            showTestPop(item) {
                this.$Modal({
                    title: `测试决策: ${item.name}`, middle: true, draggable: true,
                    component: {
                        vue: testPop,
                        datas: {decision: item}
                    },
                    width: 700, closeOnMask: false,
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
                        callback(null,  [
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
                this.$Message('保存中...');
                let decision = this.curDecision;
                $.ajax({
                    url: 'setDecision',
                    type: 'post',
                    data: {id: decision.id, dsl: decision.dsl},
                    success: (res) => {
                        if (res.code == '00') {
                            if (decision.id) {
                                $.extend(decision, res.data);
                                this.$Message.success('保存成功: ' + decision.decisionId);
                            } else {
                                this.load();
                                this.$Message.success('保存成功: ' + res.data.decisionId);
                            }
                        } else this.$Notice({type: 'error', content: res.desc, timeout: 5})
                    }
                })
            },
            add() {
                let decisionId = 'decision_' + Date.now();
                this.decision.list.unshift({
                    decisionId: decisionId,
                    name: '决策名',
                    dsl:
`// 决策id: 必须唯一
决策id = '${decisionId}'
决策名 = '${decisionId}'
决策描述 = ''

// 返回的调用决策方的结果属性值
返回属性 '身份证号码'

策略定义 {
    策略名 = 'P_预处理'

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
                console.log('showEditor', e);
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
            // showDsl(data) {
            //     this.$Modal({
            //         component: {
            //             vue: Vue.component('DecisionDetail'),
            //             datas: {decisionId: data.decisionId}
            //         },
            //         width: 600,
            //         // className: 'dsl-model',
            //         hasCloseIcon: true, fullScreen: false, middle: false, transparent: true
            //     })
            // },
            // tabSwitch(data) {
            //     console.log(data);
            //     this.$emit('tab-switch', 'PolicyConfig', data.decisionId)
            // },
            load(page) {
                if (page == undefined || page == null) page = {page: 1};
                this.decisionLoading = true;
                $.ajax({
                    url: 'mnt/decisionPage',
                    data: {page: page.page || 1, kw: this.kw},
                    success: (res) => {
                        this.decisionLoading = false;
                        if (res.code == '00') {
                            this.decision = res.data;
                        } else this.$Notice.error({content: res.desc, timeout: 5})
                    },
                    error: () => this.decisionLoading = false
                })
            }
        }
    };
</script>
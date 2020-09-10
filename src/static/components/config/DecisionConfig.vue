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
            <span class="h-panel-title">决策集</span>
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
                            &nbsp; &nbsp;
                            <i class="h-icon-trash" @click.stop="del(item)"></i>
                        </template>
                        <div style="height: 650px; width: 100vh">
                            <div v-if="collapse && collapse.length > 0 && collapse[0] == item.decisionId " ref="dslEditor" style="height: 650px; width: 800px"></div>
                        </div>
                    </h-collapseitem>
                </h-collapse>
            </div>
<!--            <h-table ref="table" :datas="decision.list" stripe select-when-click-tr :loading="decisionLoading">-->
<!--                <h-tableitem title="ID" prop="decisionId" align="center"></h-tableitem>-->
<!--                <h-tableitem title="决策名" prop="name" align="center"></h-tableitem>-->
<!--                <h-tableitem title="描述说明" prop="comment" align="center"></h-tableitem>-->
<!--                <h-tableitem title="操作" align="center" :width="100">-->
<!--                    <template slot-scope="{data}">-->
<!--                        &lt;!&ndash;                        <span class="text-hover" @click="openDecision(data)">{{data._expand?'收起':'展开'}}</span>&ndash;&gt;-->
<!--                        <span class="text-hover" @click="showDsl(data)">DSL</span>-->
<!--                        <span class="text-hover" @click="tabs.type = 'PolicyConfig'; tabs.showId = data.decisionId">策略集</span>-->
<!--                        <span class="text-hover">测试</span>-->
<!--                        &nbsp;-->
<!--                        <span class="text-hover" @click="removeDecision(data)">删除</span>-->
<!--                    </template>-->
<!--                </h-tableitem>-->

<!--                &lt;!&ndash; 下拉展示 &ndash;&gt;-->
<!--                <template slot="expand" slot-scope="{index, data}">-->
<!--                    {{data.decisionId}}-->
<!--                    &lt;!&ndash;                    <Form readonly mode="twocolumn">&ndash;&gt;-->
<!--                    &lt;!&ndash;                        <FormItem label="序号">{{index}}</FormItem>&ndash;&gt;-->
<!--                    &lt;!&ndash;                        <FormItem label="姓名">{{data.name}}</FormItem>&ndash;&gt;-->
<!--                    &lt;!&ndash;                        <FormItem label="年龄">{{data.age}}</FormItem>&ndash;&gt;-->
<!--                    &lt;!&ndash;                        <FormItem label="地址">{{data.address}}</FormItem>&ndash;&gt;-->
<!--                    &lt;!&ndash;                    </Form>&ndash;&gt;-->
<!--                    &lt;!&ndash;                    <Loading :loading="data.loading"></Loading>&ndash;&gt;-->
<!--                </template>-->

<!--                <div slot="empty">暂时无数据</div>-->
<!--            </h-table>-->
        </div>
        <div v-if="decision.totalRow" class="h-panel-bar">
            <h-pagination ref="pagination" :cur="decision.page" :total="decision.totalRow" :size="decision.pageSize"
                          align="right" @change="load" layout="pager,total"></h-pagination>
        </div>
    </div>
</template>
<script>
    // let detail
    // httpVueLoader('components/config/DecisionDetail.vue')().then((r) => detail = r);
    let list = [
        {id:'', decisionId: 'decision_1', name:'决策1', comment:'', dsl: 'xxxxxxx'},
        {id:'', decisionId: 'decision_2', name:'决策2', comment:'decisionId2', dsl: 'xxxxxxxxxxx'},
        {id:'', decisionId: 'decision_3', name:'决策3', comment:'decisionId3'},
    ];
    module.exports = {
        props: ['tabs'],
        data() {
            return {
                decisionLoading: false,
                decision: {
                    page: 1,
                    pageSize: 2,
                    totalRow: list.length,
                    list: list
                },
                collapse: null, curDecision: null,
                kw: '',
            };
        },
        mounted: function () {
            this.load()
        },
        watch: {
        },
        methods: {
            initEditor() {
                // if (this.editor) {this.editor.destroy()}
                this.editor = ace.edit(this.$refs.dslEditor[0]);
                console.log('editor: ', this.editor);
                this.editor.session.setValue(this.curDecision.dsl);
                console.log('$options', this.editor.$options);
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
`// 决策编辑: Ctrl+s 保存
// 决策id: 必须唯一
决策id = '${decisionId}'
决策名 = '${decisionId}'
决策描述 = ''

// 返回的调用决策方的结果属性值
返回属性 '身份证号码'

策略定义 {
    策略id = ''
    策略名 = ''
    策略描述 = ''

    规则定义 {
        规则名 = ''
        规则id = ''
        规则描述 = ''

        拒绝 {
            身份证号码 == null
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
                        loadJs('ace', () => {
                            loadJs('ace-ext', this.initEditor)
                        });
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
                    error: () => {
                        this.decisionLoading = false;
                    }
                })
            }
        }
    };
</script>
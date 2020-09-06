<style scoped>
    .dsl-model{
        width: 80vw;
        height: 80vh;
        overflow-y: auto;
    }
</style>
<template>
    <div class="h-panel">
        <div class="h-panel-bar">
            <span class="h-panel-title">决策集</span>
            <span v-color:gray v-font="13">说明~~</span>
            <i class="h-icon-plus" @click="add"></i>
            <div class="h-panel-right">
                <h-search placeholder="查询" v-width="200" v-model="kw" show-search-button search-text="搜索" @search="load"></h-search>
<!--                <i class="h-split"></i>-->
<!--                <button class="h-btn h-btn-green h-btn-m" @click="load">查询</button>-->
            </div>
        </div>
        <div class="h-panel-body">
            <div>
                <h-collapse v-model="collapse" accordion @change="showEditor">
                    <h-collapseitem v-for="item in decision.list" :key="item.decisionId" :name="item.decisionId" :title="item.name + '(' + item.decisionId + ')' + (item.comment ? ': ' + item.comment : '')">
                        <div style="height: 650px; width: 100vh">
                            <div v-if="collapse && collapse.length > 0 && collapse[0] == item.decisionId " id="dslEditor" style="height: 650px; width: 800px"></div>
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
        {decisionId: 'decision_1', name:'决策1', comment:'', dsl: 'xxxxxxx'},
        {decisionId: 'decision_2', name:'决策2', comment:'decisionId2', dsl: 'xxxxxxxxxxx'},
        {decisionId: 'decision_3', name:'决策3', comment:'decisionId3'},
    ];
    let data = {
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
    module.exports = {
        props: ['tabs'],
        data() {
            return data;
        },
        mounted: function () {
            //this.load()
        },
        watch: {
        },
        methods: {
            initEditor() {
                // ace.require("ace/ext/language_tools");
                this.editor = ace.edit("dslEditor");
                console.log(this.editor);
                //this.editor.setTheme("ace/theme/twilight");
                this.editor.resize();
                // this.editor.keyBinding.addCommand({
                //     name: 'myCommand',
                //     bindKey: {win: 'Ctrl-S', mac: 'Command-S'},
                //     exec(editor) {
                //         console.log('save: ' + editor.session.getValue());
                //     }
                // });
                //console.log(CommandManager);
                // this.editor.onChange = (e) => {
                //     console.log(e)
                // };
                // this.editor.session.setMode("ace/mode/groovy");
                // this.editor.setMode("ace/mode/javascript");
                this.editor.on('change', (e) => {
                    console.log(e)
                });
                // this.editor.addCommandKeyListener({
                //     name: 'myCommand',
                //     bindKey: {win: 'Ctrl-S', mac: 'Command-S'},
                //     exec(editor) {
                //         console.log('save: ' + editor.session.getValue());
                //     }
                // });
                // this.editor.commands.addCommand({
                //     name: 'myCommand',
                //     bindKey: {win: 'Ctrl-S', mac: 'Command-S'},
                //     exec(editor) {
                //         console.log('save: ' + editor.session.getValue());
                //     },
                //     // readOnly: false // 如果不需要使用只读模式，这里设置false
                // });
            },
            save() {

            },
            add() {
                this.decision.list.unshift({
                    decisionId: 'decision_',
                    name: '决策名',
                    dsl: `
// 唯一
决策id = 'decision_'
决策名 = '决策名'
决策描述 = ''

返回属性 '身份证号码'

策略定义 {
    策略名 = ''
    策略描述 = ''

    规则定义 {
        规则名 = ''
        规则id = ''

        拒绝 {
            身份证号码 == null
        }
    }
}
                    `
                })
            },
            showEditor(e) {
                if (e && e.length > 0) {
                    if (window.ace == undefined) {
                        loadJs('ace', this.initEditor);
                        // loadJs('ace', () => {
                        //     loadJs('ace-mode-groovy', this.initEditor)
                        // });
                    } else {
                        this.$nextTick(this.initEditor)
                        // this.initEditor()
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
            removeDecision(data) {
                this.$Confirm('确定删除？', `删除决策: ${data.decisionId}`).then(() => {
                    $.ajax({
                        url: 'mnt/deleteDecision/' + data.decisionId,
                        success: (res) => {
                            if (res.code == '00') {
                                this.$Message.success('删除成功');
                                this.load();
                            } else this.$Notice({type: 'error', content: res.desc, timeout: 5})
                        }
                    });
                }).catch(() => {
                    this.$Message.error('取消');
                });
            },
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
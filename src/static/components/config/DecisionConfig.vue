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
                <h-collapse v-model="collapse" accordion>
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
                        <ace-groovy v-if="collapse && collapse.length > 0 && collapse[0] == item.decisionId"
                                    v-model="item.dsl" height="650px" width="90%" @save="save(item)"
                                    :readonly="sUser.permissions.find((e) => e == 'decision-update') != 'decision-update'">
                        </ace-groovy>
<!--                        <div style="height: 650px; width: 100vh">-->
<!--                            <div v-if="collapse && collapse.length > 0 && collapse[0] == item.decisionId " ref="dslEditor" style="height: 650px; width: 800px"></div>-->
<!--                        </div>-->
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
    const apiConfig = {
        props: ['decision'],
        // language=HTML
        template: `
            <div class="h-panel">
                <div class="h-panel-bar">
                    <h-button @click="add"><i class="h-icon-plus"></i></h-button>
                    <div class="h-panel-right">
                        <h-button @click="save">保存</h-button>
                    </div>
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
                                    <span slot="close">非必须</span>
                                </h-switch>
                            </template>
                        </h-tableitem>
                        <h-tableitem title="值" align="center" :width="250">
                            <template slot-scope="{data}">
                                <h-form>
                                    <h-formitem v-if="data.type == 'Str'" label="固定值">
                                        <input type="text" v-model="data.fixValue" />
                                    </h-formitem>
                                    <h-formitem v-if="data.type == 'Str'" label="默认值">
                                        <input type="text" v-model="data.defaultValue"/>
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
                                        <h-select v-model="data.fixValue" :datas="['true', 'false']" />
                                    </h-formitem>
                                    <h-formitem v-if="data.type == 'Bool'" label="默认值">
                                        <h-select v-model="data.defaultValue" :datas="['true', 'false']" />
                                    </h-formitem>
                                </h-form>
                            </template>
                        </h-tableitem>
                        <h-tableitem title="验证" align="center" :width="250">
                            <template slot-scope="{data}">
                                <h-form>
                                    <h-formitem v-if="data.type == 'Time'" label="格式">
                                        <input type="text" v-model="data.format" placeholder="例: yyyy-MM-dd HH:mm:ss" />
                                    </h-formitem>

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
                <div class="h-panel-bar"></div>
            </div>
        `,
        data() {
            this.decision.apiConfigO = this.decision.apiConfigO ? this.decision.apiConfigO : (this.decision.apiConfig ? JSON.parse(this.decision.apiConfig) : null);
            if (this.decision.apiConfigO) {
                for (let item of this.decision.apiConfigO) {
                    if (item.code == 'decisionId' || item.code == 'async') item._readonly = true
                }
            }
            return {
                apiConfig: this.decision.apiConfigO,
                types: [
                    { title: '字符串', key: 'Str'},
                    { title: '整形', key: 'Int' },
                    { title: '布尔', key: 'Bool' },
                    { title: '小数', key: 'Decimal' },
                    { title: '时间', key: 'Time' },
                ]
            }
        },
        methods: {
            add() {
                this.decision.apiConfigO.push({name: `参数${this.decision.apiConfigO.length + 1}`, code: null, type: 'Str'})
            },
            save() {
                this.$emit('update');
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
                    <input type="text" :value="url" placeholder="请求地址" style="width: 100%"/>
                </h-cell>
            </h-row>
            <h-row :space="10" v-for="(param,index) in items">
                <h-cell width="8"><input type="text" v-model="param.code" placeholder="参数名" style="float: left; width: 100%" :readonly="param.type"/></h-cell>
                <h-cell width="12">
                    <h-select v-if="param.type == 'Bool'" v-model="param.value" :datas="['true', 'false']" :placeholder="param.name" />
                    <h-select v-else-if="param.enumValues" v-model="param.value" :datas="param.enumValues" :placeholder="param.name" />
                    <input v-else-if="param.type == 'Time'" type="text" v-model="param.value" :placeholder="param.name + ', ' + param.format" style="float: left; width: 100%"/>
                    <input v-else type="text" v-model="param.value" :placeholder="param.name" style="float: left; width: 100%"/>
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
            this.decision.apiConfigO = this.decision.apiConfigO ? this.decision.apiConfigO : (this.decision.apiConfig ? JSON.parse(this.decision.apiConfig) : null);
            let cacheKey = 'rule.test.' + this.decision.decisionId;
            let items = (() => {
                let itemsStr = localStorage.getItem(cacheKey);
                if (itemsStr) return JSON.parse(itemsStr);
                return []
            })();
            return {
                url: location.protocol + '//' + location.host + '/decision',
                cacheKey: cacheKey,
                items: (() => {
                    let arr = this.decision.apiConfigO ? this.decision.apiConfigO.map(cfg => {
                        let item = items ? items.find(i => i && i.code == cfg.code) : null;
                        return $.extend({value: (cfg.fixValue != null ? cfg.fixValue : (cfg.defaultValue != null ? cfg.defaultValue : (item ? item.value : null)))}, cfg)
                    }) : [];
                    for (let index in items) {
                        let item = items[index];
                        if (item && item.code != null && !arr.find(i => i && i.code == item.code)) {
                            arr.push(item)
                        }
                    }
                    return arr
                })(),
                result: ''
            }
        },
        methods: {
            test() {
                this.result = '';
                $.ajax({
                    url: this.url,
                    data: this.items.map((param) => {let o={}; o[param.code] = param.value; return o}).reduce((o1, o2) => {let o = {...o1, ...o2}; return o}),
                    success: (res) => {
                        if (res.code == '00') {
                            this.result = JSON.stringify(res.data, null, 4).trim();
                            this.$Message.success(`测试调用: ${this.decision.name} 成功`);
                            app.$data.tmp.testResultId = res.data.id;
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
                this.curDecision = item;
                this.$Modal({
                    title: `API配置: ${item.name}`, middle: true, draggable: true,
                    component: {
                        vue: apiConfig,
                        datas: {decision: item}
                    },
                    width: 1000, closeOnMask: false,
                    hasCloseIcon: true, fullScreen: false, middle: false, transparent: false,
                    events: {
                        update: () => {
                            this.save()
                        }
                    }
                })
            },
            showTestPop(item) {
                this.$Modal({
                    title: `测试: ${item.name}`, middle: true, draggable: true,
                    component: {
                        vue: testPop,
                        datas: {decision: item}
                    },
                    width: 750, closeOnMask: false,
                    hasCloseIcon: true, fullScreen: false, middle: false, transparent: false,
                })
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
            save(decision) {
                decision = decision || this.curDecision;
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
                            "code": "async", "name": "是否异步", "type": "Bool", "require": false, "defaultValue": 'false'
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

    条件 { // 执行此策略的条件, false: 不执行, true 或者 不配置默认 执行此策略
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
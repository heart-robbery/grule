// web socket
function ws(cfg) {
    cfg = cfg || {};
    // web socket
    let client = window.client;
    if (client && (client.readyState == 1 || client.readyState == 2)) {
        console.log('Websocket连接可用不用重创建. state: ' + client.readyState);
        return;
    }
    function doCreate() {
        console.log('create websocket ...');
        try {
            let protocol = "ws://";
            if (window.location.protocol.startsWith("https")) protocol = "wss://";
            client = new WebSocket(protocol + window.location.host + (cfg.path || "/test/msg"));
            window.client = client;
        } catch (e) {
            console.log('创建websocket错误', e);
            setTimeout(function () {
                ws(cfg);
            }, cfg.reconnection || (1000 * 60 * 2)); // 每两分钟重试
            return
        }
        client.onclose = cfg.onClose || function (e) {
            console.log('websocket close', e);
            // setTimeout(function () {
            //     ws(cfg)
            // }, cfg.reconnection || (1000 * 60 * 2)); // 每两分钟重试
        };
        client.onmessage = cfg.onMsg || function (e) { //接收websocket 消息
            let jo = toJSON(e.data);
            if (jo) {
                // {type: 'xxx': data: null}
                // if (jo.type == 'xxx') {}
                if (jo.data.msg) {
                    app.$Notice.info({title: '后台提示', content: jo.data.msg, duration: 5})
                }
            } else {
                app.$Notice.info({title: '后台提示', content: e.data, duration: 7})
            }
        };
        client.onopen = cfg.onOpen || function() {
            console.log('websocket onopen');
            client.send('成功连接...');
        };
    }
    doCreate()
}

function toJSON(str) {
    if (typeof str == 'string') {
        try {
            let obj = JSON.parse(str);
            return obj
        } catch(e) {}
    }
    return null
}


// 异步加载全局js库: js库名 -> 路径/[路径, 全局变量名]
let jsMap = new Map([
    ['md5', 'js/lib/md5.min.js'],
    ['ace', ['js/lib/ace-1.4.12.js', 'ace']],
    ['ace-tools', 'js/lib/ext-language_tools.min.js'],
    ['ace-ext-searchbox', 'js/lib/ext-searchbox.js'],
    ['ace-lang-rule', 'js/lib/mode-rule.js'],
    ['ace-snip-rule', 'js/lib/rule-snippets.js'],
    ['ace-lang-groovy', 'js/lib/mode-groovy.js'],
    ['ace-lang-json', 'js/lib/mode-json.js'],
    ['ace-snip-groovy', 'js/lib/groovy-snippets.min.js'],
    ['moment', 'js/lib/moment.min.js'],
    ['echarts', ['js/lib/echarts.min.js', 'echarts']],
]);
function loadJs() {
    if (arguments.length < 1) return;
    let names = [...arguments];
    let cb = arguments[arguments.length - 1]; // 最后一个参数可为 回调函数
    if (typeof cb == "string") {
        cb = null
    } else {
        names.pop()
    }
    let length = names.length;
    names.forEach(((item, index) => {
        length--
        let value = jsMap.get(item);
        if (!value) {
            if (length === 0 && cb) cb();
            return;
        }
        let path = value;
        if (value instanceof Array) {
            if (typeof window[(value[1])] !== "undefined") {
                path = null;
                if (length === 0 && cb) cb();
            } else {
                path = value[0]
            }
        }
        if (path) {
            $.ajax({
                url: path,
                success: (res) => {
                    let script = document.createElement( "script" );
                    script.text = res;
                    document.head.appendChild(script).parentNode.removeChild(script);
                    jsMap.delete(item); //只加载一次
                    if (length === 0 && cb) cb();
                }
            })
        }
    }));
}


// ================定义组件=====================
// 时间戳格式化组件
Vue.component('date-item', (resolve, reject) => {
    loadJs('moment', () => {
        resolve({
            props: ['time', 'format'],
            template: '<span>{{timeStr}}</span>',
            computed: {
                timeStr: function () {
                    if (!this.time) return '';
                    if (this.format) return moment(this.time).format(this.format);
                    else return moment(this.time).format('YYYY-MM-DD HH:mm:ss')
                }
            }
        })
    })
});

// json 编辑器
Vue.component('ace-json', (resolve, reject) => {
    loadJs('ace', () => {
        ace.config.set("basePath", "js/lib");
        loadJs('ace-tools', 'ace-lang-json', () => {
            resolve({
                model: {prop: 'content', event: 'update'},
                props: {content: String, width: String, height: String, readonly: Boolean},
                template: '<div ref="editor" v-bind:style="{height: heightPx, width: widthPx}"></div>',
                data() {
                    return {
                        widthPx: this.width || '100%', heightPx: this.height || '250px',
                        editor: null
                    }
                },
                mounted: function () {
                    this.$nextTick(this.initEditor)
                },
                watch: {
                    content(v) {
                        if (this.editor && v != this.editor.session.getValue()) {
                            this.editor.session.setValue(v);
                        }
                    }
                },
                methods: {
                    initEditor() {
                        // if (this.editor) {this.editor.destroy()}
                        this.editor = ace.edit(this.$refs.editor);
                        if (this.content) this.editor.session.setValue(this.content);
                        this.editor.setReadOnly(this.readonly == true ? true : false);
                        this.editor.setOptions({
                            enableBasicAutocompletion: true,
                            enableSnippets: true,
                            enableLiveAutocompletion: true
                        });
                        this.editor.on('change', (e) => {
                            // this.content = this.editor.session.getValue();
                            this.$emit('update', this.editor.session.getValue())
                        });
                        this.editor.session.setMode('ace/mode/json');
                        this.editor.commands.addCommand({
                            name: 'save',
                            bindKey: {win: 'Ctrl-S', mac: 'Command-S'},
                            exec: (editor) => {
                                // this.content = this.editor.session.getValue();
                                this.$emit('update', this.editor.session.getValue());
                                this.$emit('save', this.editor.session.getValue());
                            },
                            // readOnly: false // 如果不需要使用只读模式，这里设置false
                        });
                    },
                }
            })
        });
    });
});

// groovy 编辑器
Vue.component('ace-groovy', (resolve, reject) => {
    loadJs('ace', () => {
        ace.config.set("basePath", "js/lib");
        loadJs('ace-tools', 'ace-lang-groovy', 'ace-snip-groovy', () => {
            resolve({
                model: {prop: 'content', event: 'update'},
                props: {content: String, width: String, height: String, readonly: Boolean},
                template: '<div ref="editor" v-bind:style="{height: heightPx, width: widthPx}"></div>',
                data() {
                    return {
                        widthPx: this.width || '100%', heightPx: this.height || '250px',
                        editor: null
                    }
                },
                beforeDestroy() {
                    if (this.editor) {this.editor.destroy()}
                },
                mounted: function () {
                    this.$nextTick(this.initEditor)
                },
                watch: {
                    content(v) {
                        if (this.editor && v != this.editor.session.getValue()) {
                            this.editor.session.setValue(v);
                        }
                    }
                },
                methods: {
                    initEditor() {
                        // if (this.editor) {this.editor.destroy()}
                        this.editor = ace.edit(this.$refs.editor);
                        if (this.content) this.editor.session.setValue(this.content);
                        this.editor.setReadOnly(this.readonly == true ? true : false);
                        this.editor.setOptions({
                            enableBasicAutocompletion: true,
                            enableSnippets: true,
                            enableLiveAutocompletion: true
                        });
                        this.editor.on('change', (e) => {
                            // this.content = this.editor.session.getValue();
                            this.$emit('update', this.editor.session.getValue())
                        });
                        this.editor.session.setMode('ace/mode/groovy');
                        //this.editor.execCommand('find');
                        this.editor.commands.addCommand({
                            name: 'save',
                            bindKey: {win: 'Ctrl-S', mac: 'Command-S'},
                            exec: (editor) => {
                                // this.content = this.editor.session.getValue();
                                this.$emit('update', this.editor.session.getValue());
                                this.$emit('save', this.editor.session.getValue());
                            },
                            // readOnly: false // 如果不需要使用只读模式，这里设置false
                        });
                        // let languageTools = ace.require("ace/ext/language_tools");
                        // console.log('languageTools', ace.require("ace/ext/language_tools"));
                        // languageTools.addCompleter({
                        //     getCompletions: (editor, session, pos, prefix, callback) => {
                        //         callback(null, [
                        //             {
                        //                 name : "第一行", //名称
                        //                 value : "身份证号码",//值，这就是匹配我们输入的内容，比如输入s或者select,这一行就会出现在提示框里，可根据自己需求修改，就是你想输入什么显示出北京呢，就改成什么
                        //                 caption: "身",//字幕，下拉提示左侧内容,这也就是我们输入前缀匹配出来的内容，所以这里必须包含我们的前缀
                        //                 meta: "字段-身份证号码", //类型，下拉提示右侧内容
                        //                 type: "local",//可写为keyword
                        //                 score : 1000 // 让它排在最上面，类似权值的概念
                        //             },
                        //             {
                        //                 name : "年龄", //名称
                        //                 value : "年龄",//值，这就是匹配我们输入的内容，比如输入s或者select,这一行就会出现在提示框里，可根据自己需求修改，就是你想输入什么显示出北京呢，就改成什么
                        //                 caption: "年",//字幕，下拉提示左侧内容,这也就是我们输入前缀匹配出来的内容，所以这里必须包含我们的前缀
                        //                 meta: "字段-年龄", //类型，下拉提示右侧内容
                        //                 type: "local",//可写为keyword
                        //                 score : 1000 // 让它排在最上面，类似权值的概念
                        //             }
                        //         ]);
                        //     }
                        // });
                    },
                }
            })
        });
    });
});


// 异步加载组件
new Map([
    ['Upload', 'Upload.vue'],
    ['Admin', 'Admin.vue'],
    ['Login', 'Login.vue'],
    ['Dashboard', 'Dashboard.vue'],
    ['PolicyCenter', 'config/PolicyCenter.vue'],
    ['DecisionConfig', 'config/DecisionConfig.vue'],
    ['DecisionDetail', 'config/DecisionDetail.vue'],
    ['PolicyDetail', 'config/PolicyDetail.vue'],
    ['RuleDetail', 'config/RuleDetail.vue'],
    ['FieldConfig', 'config/FieldConfig.vue'],
    ['DataCollectorConfig', 'config/DataCollectorConfig.vue'],
    ['OpHistory', 'config/OpHistory.vue'],
    ['UserCenter', 'config/UserCenter.vue'],
    ['UserConfig', 'config/UserConfig.vue'],
    ['Permission', 'config/Permission.vue'],
    ['DecisionData', 'data/DecisionData.vue'],
    ['DecisionResult', 'data/DecideResult.vue'],
    ['CollectResult', 'data/CollectResult.vue'],
    ['DataAnalyse', 'data/DataAnalyse.vue'],
    ['DecisionEChart', 'data/DecisionEChart.vue'],
    ['RuleEChart', 'data/RuleEChart.vue'],
    ['Clock', 'Clock.vue'],
    ['StatusSuspension', 'StatusSuspension.vue'],
    ['MyInfo', 'MyInfo.vue'],
]).forEach((value, key) => {
    Vue.component(key, (resolve, reject) => {
        httpVueLoader('components/'+ value)().then((r) => resolve(r))
    });
});

<style scoped>

</style>
<template>
    <div class="h-panel">
        <div class="h-panel-bar">
            <span class="h-panel-title">决策({{name}})DSL</span>
            <div class="h-panel-right">
            </div>
        </div>
        <div class="h-panel-body">
            <div id="dslEditor" style="height: 500px; width: 500px">{{dsl}}</div>
        </div>
    </div>
</template>

<script>
    let data = {name: null, dsl: '', ace: null, editor: null};
    loadJs('ace',() => {data.ace = window.ace;});
    module.exports = {
        props: ['decisionId'],
        data() {
            return data
        },
        mounted() {
            this.load();
            if (window.ace) {
                this.$nextTick(this.initEditor)
            }
        },
        watch: {
            ace() {
                if (ace) this.initEditor()
            },
            dsl() {
                this.editor.setValue(this.dsl);
            }
        },
        methods: {
            initEditor() {
                this.editor = ace.edit("dslEditor");
                this.editor.resize();
                this.editor.on('onchange', (e) => {
                    console.log(e)
                })
            },
            load() {
                if (!this.decisionId) {
                    this.$Message.error('参数错误: decisionId');
                    return
                }
                $.ajax({
                    url: 'mnt/decisionDetail/' + this.decisionId,
                    success: (res) => {
                        if (res.code == '00') {
                            $.extend(data, res.data);
                        } else this.$Message.error(res.desc)
                    }
                })
            }
        }
    }
</script>
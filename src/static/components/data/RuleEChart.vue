<template>
    <div class="h-panel">
        <div class="h-panel-bar">
            <span class="h-panel-title">决策规则统计</span>
            <div style="float: right">
                <h-autocomplete v-model="model.decisionId" :option="decisionAc" style="float:left; width: 180px" @change="load((() => chart.setOption(option)))" placeholder="决策"></h-autocomplete>
            </div>
            <h-datepicker v-model="model.startTime" type="datetime" :option="{minuteStep:2}" :has-seconds="true" placeholder="开始时间" style="width: 160px"></h-datepicker>
            -
            <h-datepicker v-model="model.endTime" type="datetime" :option="{minuteStep:2}" :has-seconds="true" placeholder="结束时间" style="width: 160px"></h-datepicker>
        </div>
        <div class="h-panel-body">
            <div ref="main" style="width: 100%; height: 250px; overflow-y: auto; overflow-x: hidden"></div>
            <h-loading text="Loading" :loading="loading"></h-loading>
        </div>
    </div>
</template>
<script>
    module.exports = {
        data() {
            return {
                loading: false,
                decisionAc: {
                    keyName: 'decisionId',
                    titleName: 'name',
                    minWord: 1,
                    loadData: (filter, cb) => {
                        $.ajax({
                            url: 'mnt/decisionPage',
                            data: {page: 1, pageSize: 5, nameLike: filter},
                            success: (res) => {
                                this.isLoading = false;
                                if (res.code === '00') {
                                    cb(res.data.list.map((r) => {
                                        return {decisionId: r.id, name: r.name}
                                    }))
                                } else this.$Notice.error(res.desc)
                            },
                        });
                    }
                },
                taskId: null,
                model: {startTime: (function () { // 从0点开始的时间
                        let d = new Date();
                        let month = d.getMonth() + 1;
                        return d.getFullYear() + "-" + (month < 10 ? '0' + month : month) + "-" + (d.getDate() < 10 ? '0' + d.getDate() : d.getDate()) + " 00:00:00"
                    })()},
                option: {
                    tooltip: {
                        trigger: 'axis',
                        axisPointer: {            // 坐标轴指示器，坐标轴触发有效
                            type: 'shadow'        // 默认为直线，可选为：'line' | 'shadow'
                        },
                    },
                    legend: {
                        data: ['通过', '拒绝', '人工']
                    },
                    grid: {
                        left: '3%',
                        right: '4%',
                        bottom: '3%',
                        containLabel: true
                    },
                    xAxis: {
                        type: 'value'
                    },
                },
                chart: null,
            }
        },
        mounted() {
            this.load(() => loadJs('echarts', () => this.$nextTick(this.initEChart)));
        },
        activated() {
            if (this.chart) this.load(() => this.chart.setOption(this.option));
        },
        deactivated() {
            if (this.taskId) clearInterval(this.taskId)
        },
        beforeDestroy() {
            if (this.taskId) clearInterval(this.taskId)
        },
        watch: {
            'model.startTime'() {
                this.load((() => this.chart.setOption(this.option)))
            },
            'model.endTime'() {
                this.load((() => this.chart.setOption(this.option)))
            }
        },
        methods: {
            initEChart() {
                this.chart = echarts.init(this.$refs.main);
                // console.log('chart', this.chart);
                this.chart.setOption(this.option);
                if (this.option.yAxis.data.length > 3) {
                    this.chart.getDom().style.height = (Math.min(this.option.yAxis.data.length * 80, 800)) + 'px';
                } else {
                    this.chart.getDom().style.height = '250px'
                }
                this.chart.resize()
                // console.log('option', this.option)
            },
            load(cb) {
                this.loading = true
                $.ajax({
                    url: 'mnt/countRule',
                    data: this.model,
                    success: (res) => {
                        this.loading = false
                        if (res.code === '00') {
                            let cate = [];
                            for (const item of res.data.map((o) => o.decisionName + ' || ' + o.policyName  + ' || ' + o.ruleName)) { // 保留顺序去重
                                if (cate.indexOf(item) === -1) cate.push(item);
                            }
                            cate.reverse();
                            this.option.yAxis = {
                                type: 'category',
                                data: cate
                            };
                            this.option.series = [
                                {
                                    name: '拒绝',
                                    type: 'bar',
                                    stack: '总量',
                                    label: {
                                        show: true,
                                        position: 'insideRight',
                                        normal: {
                                            show: true,
                                            formatter: function (params) {
                                                if (params.value > 0) {
                                                    return params.value;
                                                } else {
                                                    return '';
                                                }
                                            }
                                        }
                                    },
                                    data: (function () {
                                        let arr = [];
                                        cate.forEach(function(value, index, array){
                                            let item = res.data.find(o => o.result === 'Reject' && (o.decisionName + ' || ' + o.policyName + ' || ' + o.ruleName) == value);
                                            arr.push(item ? item.total : 0);
                                        });
                                        return arr;
                                    })()
                                },
                                {
                                    name: '人工',
                                    type: 'bar',
                                    stack: '总量',
                                    label: {
                                        show: true,
                                        position: 'insideRight',
                                        normal: {
                                            show: true,
                                            formatter: function (params) {
                                                if (params.value > 0) {
                                                    return params.value;
                                                } else {
                                                    return '';
                                                }
                                            }
                                        }
                                    },
                                    data: (function () {
                                        let arr = [];
                                        cate.forEach(function(value, index, array){
                                            let item = res.data.find(o => o.result === 'Review' && (o.decisionName + ' || ' + o.policyName + ' || ' + o.ruleName) == value);
                                            arr.push(item ? item.total : 0);
                                        });
                                        return arr;
                                    })()
                                },
                                {
                                    name: '通过',
                                    type: 'bar',
                                    stack: '总量',
                                    label: {
                                        show: true,
                                        position: 'insideRight',
                                        normal: {
                                            show: true,
                                            formatter: function (params) {
                                                if (params.value > 0) {
                                                    return params.value;
                                                } else {
                                                    return '';
                                                }
                                            }
                                        }
                                    },
                                    data: (function () {
                                        let arr = [];
                                        cate.forEach(function(value, index, array){
                                            let item = res.data.find(o => o.result === 'Accept' && (o.decisionName + ' || ' + o.policyName + ' || ' + o.ruleName) == value);
                                            arr.push(item ? item.total : 0);
                                        });
                                        return arr;
                                    })()
                                }
                            ];
                            if (cb) cb()
                            if (this.chart) { //随数据多少自适应高度
                                if (cate.length > 3) {
                                    this.chart.getDom().style.height = (Math.min(cate.length * 80, 800)) + 'px';
                                } else {
                                    this.chart.getDom().style.height = '250px'
                                }
                                this.chart.resize()
                            }
                        } else this.$Message.error(res.desc)
                    },
                    error: () => this.loading = false
                })
            }
        }
    }
</script>
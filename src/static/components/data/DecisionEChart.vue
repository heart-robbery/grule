<template>
    <div class="h-panel">
        <div class="h-panel-bar">
            <span class="h-panel-title">决策统计</span>
            <h-datepicker v-model="model.startTime" type="datetime" :option="{minuteStep:2}" :has-seconds="true" placeholder="开始时间" style="width: 160px"></h-datepicker>
            -
            <h-datepicker v-model="model.endTime" type="datetime" :option="{minuteStep:2}" :has-seconds="true" placeholder="结束时间" style="width: 160px"></h-datepicker>
        </div>
        <div class="h-panel-body bottom-line">
            <div ref="main" v-bind:style="{width: widthPx, height: heightPx, minHeight: minHeight}"></div>
        </div>
    </div>
</template>
<script>
    module.exports = {
        data() {
            return {
                widthPx: '100%', heightPx: '100%', minHeight: '400px',
                taskId: null,
                model: {startTime: (function () {
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
            // setTimeout(() => {
            //     this.taskId = setInterval(() => {
            //         this.load(() => this.chart.setOption(this.option));
            //     }, 1000 * 60 * 5)
            // })
        },
        activated() {
            if (this.chart) this.load(() => this.chart.setOption(this.option));
            // this.taskId = setInterval(() => {
            //     this.load(() => this.chart.setOption(this.option));
            // }, 1000 * 60 * 5)
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
                // console.log('option', this.option)
            },
            load(cb) {
                $.ajax({
                    url: 'mnt/countDecide',
                    data: this.model,
                    success: (res) => {
                        if (res.code == '00') {
                            let cate = Array.from(new Set(res.data.map((o) => o.decisionName)));
                            this.minHeight = cate.length < 2 ? '200px' : (Math.min(cate.length * 80, 800)) + 'px';
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
                                            let item = res.data.find(o => o.result == 'Reject' && o.decisionName == value);
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
                                            let item = res.data.find(o => o.result == 'Review' && o.decisionName == value);
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
                                            let item = res.data.find(o => o.result == 'Accept' && o.decisionName == value);
                                            arr.push(item ? item.total : 0);
                                        });
                                        return arr;
                                    })()
                                }
                            ];
                            if (cb) cb()
                        } else this.$Message.error(res.desc)
                    }
                })
            }
        }
    }
</script>
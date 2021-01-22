<template>
    <div>
        <!--            <h-datepicker v-model="model.startTime" type="datetime" :has-seconds="true" placeholder="开始时间"></h-datepicker>-->
        <!--            <h-datepicker v-model="model.endTime" type="datetime" :has-seconds="true" placeholder="结束时间"></h-datepicker>-->
        <h-select v-model="model.type" :datas="types" style="width: 90px" @change="load((() => chart.setOption(option)))"></h-select>
        <div ref="main" style="width: 100%; height: 400px;"></div>
    </div>
</template>
<script>
    module.exports = {
        data() {
            return {
                taskId: null,
                model: {type: localStorage.getItem('rule.dashboard.type') || 'today'},
                types: [
                    { title: '今天', key: 'today'},
                    { title: '近1小时', key: 'lastOneHour'},
                    { title: '近2小时', key: 'lastTwoHour'},
                    { title: '近5小时', key: 'lastFiveHour'},
                ],
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
            //     }, 1000 * 60 * 3)
            // })
        },
        activated() {
            if (this.chart) this.load(() => this.chart.setOption(this.option));
            // this.taskId = setInterval(() => {
            //     this.load(() => this.chart.setOption(this.option));
            // }, 1000 * 60 * 3)
        },
        deactivated() {
            if (this.taskId) clearInterval(this.taskId)
        },
        beforeDestroy() {
            if (this.taskId) clearInterval(this.taskId)
        },
        watch: {
            'model.type': (v) => {
                localStorage.setItem('rule.dashboard.type', v);
            }
        },
        methods: {
            initEChart() {
                // this.option = {
                //     tooltip: {
                //         trigger: 'axis',
                //         axisPointer: {            // 坐标轴指示器，坐标轴触发有效
                //             type: 'shadow'        // 默认为直线，可选为：'line' | 'shadow'
                //         }
                //     },
                //     legend: {
                //         data: ['直接访问', '邮件营销', '联盟广告', '视频广告', '搜索引擎']
                //     },
                //     grid: {
                //         left: '3%',
                //         right: '4%',
                //         bottom: '3%',
                //         containLabel: true
                //     },
                //     xAxis: {
                //         type: 'value'
                //     },
                //     yAxis: {
                //         type: 'category',
                //         data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
                //     },
                //     series: [
                //         {
                //             name: '直接访问',
                //             type: 'bar',
                //             stack: '总量',
                //             label: {
                //                 show: true,
                //                 position: 'insideRight'
                //             },
                //             data: [320, 302, 301, 334, 390, 330, 320]
                //         },
                //         {
                //             name: '邮件营销',
                //             type: 'bar',
                //             stack: '总量',
                //             label: {
                //                 show: true,
                //                 position: 'insideRight'
                //             },
                //             data: [120, 132, 101, 134, 90, 230, 210]
                //         },
                //         {
                //             name: '联盟广告',
                //             type: 'bar',
                //             stack: '总量',
                //             label: {
                //                 show: true,
                //                 position: 'insideRight'
                //             },
                //             data: [220, 182, 191, 234, 290, 330, 310]
                //         },
                //         {
                //             name: '视频广告',
                //             type: 'bar',
                //             stack: '总量',
                //             label: {
                //                 show: true,
                //                 position: 'insideRight'
                //             },
                //             data: [150, 212, 201, 154, 190, 330, 410]
                //         },
                //         {
                //             name: '搜索引擎',
                //             type: 'bar',
                //             stack: '总量',
                //             label: {
                //                 show: true,
                //                 position: 'insideRight'
                //             },
                //             data: [820, 832, 901, 934, 1290, 1330, 1320]
                //         }
                //     ]
                // };
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
                                            let item = res.data.find(o => o.decision == 'Reject' && o.decisionName == value);
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
                                            let item = res.data.find(o => o.decision == 'Review' && o.decisionName == value);
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
                                            let item = res.data.find(o => o.decision == 'Accept' && o.decisionName == value);
                                            arr.push(item ? item.total : 0);
                                        });
                                        return arr;
                                    })()
                                }
                            ];
                            if (cb) cb()
                        } else this.$Notice.error(res.desc)
                    }
                })
            }
        }
    }
</script>
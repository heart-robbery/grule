<style scoped>

</style>
<template>
    <div style="background: rgb(255, 255, 255); padding: 24px; min-height: 100vh;">
        <div>
            <h-datepicker v-model="model.startTime" type="datetime" :has-seconds="true" placeholder="开始时间"></h-datepicker>
            <h-datepicker v-model="model.endTime" type="datetime" :has-seconds="true" placeholder="结束时间"></h-datepicker>
            <h-select v-model="model.type" :datas="types" style="width: 90px" @change="load"></h-select>
        </div>
        <div ref="main" style="width: 100%; height: 400px;"></div>
    </div>
</template>
<script>
    module.exports = {
        data() {
            return {
                model: {type: localStorage.getItem('rule.dashbord.type') || 'today'},
                types: [
                    { title: '今天', key: 'today'},
                    { title: '近1小时', key: 'lastOneHour'},
                    { title: '近2小时', key: 'lastTwoHour'},
                    { title: '近5小时', key: 'lastFiveHour'},
                ]
            };
        },
        mounted() {
            loadJs('echarts', () => this.$nextTick(this.initEChart));
        },
        watch: {
            'model.type': (v) => {
                localStorage.setItem('rule.dashbord.type', v);
            }
        },
        methods: {
            initEChart() {
                let option = {
                    tooltip: {
                        trigger: 'axis',
                        axisPointer: {            // 坐标轴指示器，坐标轴触发有效
                            type: 'shadow'        // 默认为直线，可选为：'line' | 'shadow'
                        }
                    },
                    legend: {
                        data: ['直接访问', '邮件营销', '联盟广告', '视频广告', '搜索引擎']
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
                    yAxis: {
                        type: 'category',
                        data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
                    },
                    series: [
                        {
                            name: '直接访问',
                            type: 'bar',
                            stack: '总量',
                            label: {
                                show: true,
                                position: 'insideRight'
                            },
                            data: [320, 302, 301, 334, 390, 330, 320]
                        },
                        {
                            name: '邮件营销',
                            type: 'bar',
                            stack: '总量',
                            label: {
                                show: true,
                                position: 'insideRight'
                            },
                            data: [120, 132, 101, 134, 90, 230, 210]
                        },
                        {
                            name: '联盟广告',
                            type: 'bar',
                            stack: '总量',
                            label: {
                                show: true,
                                position: 'insideRight'
                            },
                            data: [220, 182, 191, 234, 290, 330, 310]
                        },
                        {
                            name: '视频广告',
                            type: 'bar',
                            stack: '总量',
                            label: {
                                show: true,
                                position: 'insideRight'
                            },
                            data: [150, 212, 201, 154, 190, 330, 410]
                        },
                        {
                            name: '搜索引擎',
                            type: 'bar',
                            stack: '总量',
                            label: {
                                show: true,
                                position: 'insideRight'
                            },
                            data: [820, 832, 901, 934, 1290, 1330, 1320]
                        }
                    ]
                };
                let myChart = echarts.init(this.$refs.main);
                myChart.setOption(option);
            },
            load() {
                $.ajax({
                    url: '',
                    success: (res) => {
                        if (res.code == '00') {
                        } else this.$Notice.error(res.desc)
                    }
                })
            }
        },
    };
</script>
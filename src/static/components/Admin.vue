<style scoped>
    .h-layout {
        background: #f0f2f5;
        min-height: 100vh;
    }

    .layout-logo {
        height: 34px;
        background: rgba(255, 255, 255, 0.2);
        margin: 16px 24px;
    }

    .h-layout-footer {
        padding: 24px 50px;
        color: rgba(0, 0, 0, 0.65);
        font-size: 14px;
    }
</style>
<template>
    <div>
        <h-layout :sider-fixed="siderFixed" :sider-collapsed="siderCollapsed">
            <h-sider theme="dark">
                <div class="layout-logo"></div>
                <h-menu style="margin-top: 20px;" class="h-menu-dark" :datas="menuDatas"
                        :inline-collapsed="siderCollapsed"
                        ref="menu"
                        @select="changeMenu"
                ></h-menu>
            </h-sider>
            <h-layout :header-fixed="headerFixed">
                <h-header theme="white">
                    <div style="width:100px;float:left;">
                        <h-button icon="h-icon-menu" size="l" no-border style="font-size: 20px" @click="siderCollapsed=!siderCollapsed"></h-button>
                    </div>
                </h-header>
                <h-content style="padding: 0px 30px;">
                    <component v-bind:is="contentComponent"></component>
                </h-content>
                <h-footer class="text-center">Copyright © {{year}}
                    <a href="https://gitee.com/xnat/gy" target="_blank">xxb</a>
                </h-footer>
            </h-layout>
        </h-layout>
    </div>
</template>

<script>
    module.exports = {
        data: function() {
            return {
                year: (new Date()).getFullYear(),
                headerFixed: false,
                siderFixed: false,
                siderCollapsed: false,
                menuDatas: [ // key 为 组件名
                    { title: '首页', key: 'Dashboard', icon: 'h-icon-home' },
                    { title: '查询', key: 'search', icon: 'h-icon-search' },
                    { title: '收藏', key: 'favor', icon: 'h-icon-star', count: 100, children: [{ title: '收藏-1', key: 'favor2-1' }] },
                    { title: '配置中心', key: 'config', icon: 'h-icon-setting', children: [
                        { title: '策略中心', key: 'PolicyCenter' },
                        ] },
                    { title: '任务', icon: 'h-icon-task', key: 'task' }
                ],
                datas: [
                    { icon: 'h-icon-home' },
                    { title: 'Component', icon: 'h-icon-complete', route: { name: 'Component' } },
                    { title: 'Breadcrumb', icon: 'h-icon-star' }
                ],
                contentComponent: null
            };
        },
        mounted: function () {
            this.select(this.firstKey())
        },
        methods: {
            changeContent(key) {
                this.contentComponent = key
            },
            changeMenu: function(e){
                this.changeContent(e.key)
            },
            select(key) {
                this.$refs.menu.select(key);
                this.changeContent(key)
            },
            firstKey() {
                if (this.menuDatas[0].children) {
                    return this.menuDatas[0].children[0].key
                } else {
                    return this.menuDatas[0].key
                }
            }
        },
        watch: {
            siderFixed: function (){
                if (!this.siderFixed) {
                    this.headerFixed = false;
                }
            }
        }
    };
</script>
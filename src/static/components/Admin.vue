<style scoped>
    .h-layout {
        background: #f0f2f5;
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
    <div class="layout-demo-3-vue">
        <h-layout :siderFixed="siderFixed" :siderCollapsed="siderCollapsed">
            <h-sider theme="dark">
                <div class="layout-logo"></div>
                <h-menu style="margin-top: 20px;" class="h-menu-dark" :datas="menuDatas" :inlineCollapsed="siderCollapsed"></h-menu>
            </h-sider>
            <h-layout :headerFixed="headerFixed">
                <h-header theme="white">
                    <div style="width:100px;float:left;">
                        <h-button icon="h-icon-menu" size="l" noBorder style="font-size: 20px" @click="siderCollapsed=!siderCollapsed"></h-button>
                    </div>
                </h-header>
                <h-content style="padding: 0px 30px;">
<!--                    <h-breadcrumb :datas="datas" style="margin: 16px 0px;"></h-breadcrumb>-->
                    <div style="background: rgb(255, 255, 255); padding: 24px; min-height: 280px;">
                        <p>
                            <h-switch v-model="siderFixed">固定Sider</h-switch>
                        </p>
                        <p>
                            <h-switch v-model="headerFixed" :disabled="!siderFixed">固定header</h-switch>
                        </p>
                        <p>
                            <h-switch v-model="siderCollapsed">收起菜单</h-switch>
                        </p>
                    </div>
                    <h-footer class="text-center">Copyright © {{year}}
                        <a href="http://www.ch-un.com" target="_blank">Lan</a>
                    </h-footer>
                </h-content>
            </h-layout>
        </h-layout>
    </div>
</template>

<script>
    module.exports = {
        props: [],
        data: function() {
            return {
                year: (new Date()).getFullYear(),
                headerFixed: false,
                siderFixed: false,
                siderCollapsed: false,
                menuDatas: [
                    { title: '首页', key: 'welcome', icon: 'h-icon-home' },
                    { title: '查询', key: 'search', icon: 'h-icon-search' },
                    { title: '收藏', key: 'favor', icon: 'h-icon-star', count: 100, children: [{ title: '收藏-1', key: 'favor2-1' }] },
                    { title: '任务', icon: 'h-icon-task', key: 'task' }
                ],
                datas: [
                    { icon: 'h-icon-home' },
                    { title: 'Component', icon: 'h-icon-complete', route: { name: 'Component' } },
                    { title: 'Breadcrumb', icon: 'h-icon-star' }
                ]
            };
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
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
        <status-suspension></status-suspension>
        <h-layout :sider-fixed="siderFixed" :sider-collapsed="siderCollapsed">
            <h-sider theme="dark">
<!--                <div class="layout-logo">-->

<!--                </div>-->
                <Clock v-show="!siderCollapsed"></Clock>
<!--                <span>DSL Rule Language</span>-->
<!--                <div class="text-center">-->
<!--                    <h-avatar src="https://i1.go2yd.com/image.php?url=0Kvq81cKR1">-->
<!--                        &lt;!&ndash;                    <div style="font-size: 18px;">默认尺寸的显示</div>&ndash;&gt;-->
<!--                        &lt;!&ndash;                    <p class="dark2-color">描述</p>&ndash;&gt;-->
<!--                    </h-avatar>-->
<!--                </div>-->
                <h-menu style="margin-top: 20px;" class="h-menu-dark" :datas="menuDatas"
                        :inline-collapsed="siderCollapsed" accordion
                        ref="menu"
                        @select="changeMenu"
                ></h-menu>
            </h-sider>
            <h-layout :header-fixed="headerFixed">
                <h-header theme="white">
                    <div style="width:100px;float:left;">
                        <h-button icon="h-icon-menu" size="l" no-border style="font-size: 20px" @click="siderCollapsed=!siderCollapsed"></h-button>
                    </div>
                    <div class="float-right">
                        <h-dropdownmenu class-name="app-header-dropdown" trigger="hover" offset="0,5" :width="150" placement="bottom-end" :datas="infoMenu" @onclick="trigger">
                            <h-avatar src="https://admin.heyui.top/static/images/src/images/avatar.8a80923.png" :width="30"><span>{{sUser.name}}</span></h-avatar>
                        </h-dropdownmenu>
                    </div>
                </h-header>
                <h-content style="padding: 0px 30px;">
                    <keep-alive>
                        <component v-bind:is="menu.key" :menu="menu"></component>
                    </keep-alive>
                </h-content>
                <h-footer class="text-center">Copyright © {{year}}
                    <a href="https://gitee.com/xnat/rule/" target="_blank">xxb</a>
                </h-footer>
            </h-layout>
        </h-layout>
    </div>
</template>

<script>
    module.exports = {
        data: function() {
            return {
                infoMenu: [
                    { key: 'info', title: '个人信息', icon: 'h-icon-user' },
                    { key: 'logout', title: '退出登录', icon: 'h-icon-outbox' }
                ],
                sUser: app.$data.user,
                year: (new Date()).getFullYear(),
                headerFixed: false,
                siderFixed: false,
                siderCollapsed: false,
                menuDatas: [ // key 为 组件名
                    { title: 'Dashboard', key: 'Dashboard', icon: 'h-icon-home' },
                    { title: '配置中心', key: 'configCenter', icon: 'h-icon-setting', children: [
                            { title: '策略中心', key: 'PolicyCenter' },
                            { title: '用户中心', key: 'UserCenter' },
                        ] },
                    { title: '数据中心', key: 'dataCenter', icon: 'h-icon-search', children: [
                            { title: '决策数据', key: 'DecisionData' },
                            { title: '数据分析', key: 'DataAnalyse' },
                        ] },
                    // { title: '查询', key: 'search', icon: 'h-icon-search' },
                    // { title: '收藏', key: 'favor', icon: 'h-icon-star', count: 100, children: [{ title: '收藏-1', key: 'favor2-1' }] },
                    // { title: '任务', icon: 'h-icon-task', key: 'task' }
                ],
                datas: [
                    { icon: 'h-icon-home' },
                    { title: 'Component', icon: 'h-icon-complete', route: { name: 'Component' } },
                    { title: 'Breadcrumb', icon: 'h-icon-star' }
                ],
                menu: {key: null}
            };
        },
        mounted: function () {
            this.select(localStorage.getItem("rule.admin.menu") || this.firstKey())
        },
        watch: {
            siderFixed: function (){
                if (!this.siderFixed) {
                    this.headerFixed = false;
                }
            },
            'menu.key': function (v) {
                // this.select(v)
            }
        },
        methods: {
            trigger(data) {
                if (data == 'logout') {
                    $.ajax({
                        url: 'mnt/logout',
                        success: (res) => {
                            if (res.code == '00') {
                                location.reload();
                            } else this.$Notice.error(res.desc)
                        }
                    })
                } else {
                    this.changeContent('MyInfo');
                }
            },
            changeContent(key) {
                this.menu.key = key;
                localStorage.setItem("rule.admin.menu", key);
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
    };
</script>
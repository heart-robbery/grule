<style scoped>
    .h-taginput {
        width: 100%;
    }
</style>
<template>
    <div class="h-panel">
        <div class="h-panel-bar">
            <h-button @click="showAddPop"><i class="h-icon-plus"></i></h-button>
            <div class="h-panel-right">
                <!--                <i class="h-split"></i>-->
                <!--                <button class="h-btn h-btn-green h-btn-m" @click="load">查询</button>-->
                <h-search placeholder="查询" v-width="200" v-model="kw" show-search-button search-text="搜索" @search="load"></h-search>
            </div>
        </div>
        <div class="h-panel-body">
            <div style="padding: 5px 0; margin: 0 10px;" v-for="item in list" :key="item.id">
                <p style="font-size: 15px; font-weight: bold;">{{item.name}}
                    &nbsp; <span v-if="item.login">上次登录时间: <date-item :time="item.login" /></span>
                    &nbsp; <span class="h-icon-edit text-hover" @click="showUpdatePop(item)"></span>
                </p>

                <p class="tags"><h-taginput v-model="item.permissionNames" readonly></h-taginput></p>
                <pre class="desc">{{item.comment}}</pre>
            </div>
        </div>
        <div v-if="totalRow" class="h-panel-bar">
            <h-pagination ref="pagination" :cur="page" :total="totalRow" :size="pageSize" align="right" @change="load" layout="pager,total"></h-pagination>
        </div>
    </div>
</template>
<script>
    loadJs('moment');
    const addEditPop = { //添加,编辑窗口组件
        template: `
                <div v-width="600" style="padding-top: 10px">
                    <h-form ref="form"
                            :valid-on-change="true"
                            :show-error-tip="true"
                            :label-position="'left'"
                            :label-width="110"
                            :rules="validationRules"
                            :model="model">
                        <h-formitem label="用户名" icon="h-icon-user">
                            <input type="text" v-model="model.name" :readonly="user">
                        </h-formitem>
                        <h-formitem v-if="!user" label="密码" icon="h-icon-user">
                            <input type="password" v-model="model.password">
                        </h-formitem>
                        <h-formitem label="权限" icon="h-icon-complete">
                            <h-transfer v-model="model.ps" :datas="permissions" filterable></h-transfer>
                        </h-formitem>
                        <h-formitem>
                                <h-button v-if="model.id" color="primary" :loading="isLoading" @click="update">提交</h-button>
                                <h-button v-else color="primary" :loading="isLoading" @click="add">提交</h-button>
                                &nbsp;&nbsp;&nbsp;
                                <h-button v-if="model.id" @click="initModel">清除</h-button>
                                <h-button v-else @click="initModel">重置</h-button>
                        </h-formitem>
                    </h-form>
                </div>
                `,
        props: ['user'],
        data() {
            return {
                isLoading: false,
                model: this.initModel(),
                validationRules: {
                    required: ['name', 'password']
                },
                permissions: []
            }
        },
        mounted() {
            this.loadPermissions()
        },
        methods: {
            initModel() {
                return this.user ? $.extend({
                    ps: this.user.permissions ? this.user.permissions.filter(o => o).flatMap(p => Object.keys(p)) : []
                }, this.user) : {ps: []}
            },
            loadPermissions() {
                $.ajax({
                    url: 'mnt/user/permissions',
                    success: (res) => {
                        if (res.code == '00') {
                            this.permissions = res.data.map(o => {
                                let p = {key: o.enName, text: o.cnName};
                                return p;
                            });
                        } else this.$Notice.error(res.desc)
                    }
                })
            },
            update() {
                this.isLoading = true;
                $.ajax({
                    url: 'mnt/user/update',
                    type: 'post',
                    data: this.model,
                    success: (res) => {
                        this.isLoading = false;
                        if (res.code == '00') {
                            this.$emit('close');
                            this.$Message.success(`更新用户: ${this.model.name} 成功`);
                            this.$emit('reload');
                        } else this.$Notice.error(res.desc)
                    },
                    error: () => this.isLoading = false
                })
            },
            add() {
                this.isLoading = true;
                $.ajax({
                    url: 'mnt/user/add',
                    type: 'post',
                    data: this.model,
                    success: (res) => {
                        this.isLoading = false;
                        if (res.code == '00') {
                            this.$emit('close');
                            this.$Message.success(`添加用户: ${this.model.name} 成功`);
                            this.$emit('reload');
                        } else this.$Notice.error(res.desc)
                    },
                    error: () => this.isLoading = false
                })
            },
        }
    };
    module.exports = {
        data() {
            return {
                kw: '',
                loading: false,
                page: 1, totalRow: 0, pageSize: 10, list: []
            }
        },
        mounted() {
            this.load()
        },
        watch: {
            list: function () {
                if (this.list) {
                    this.list.map(o => {
                        if (o.permissions) {
                            o.permissionNames = o.permissions.filter(o => o).flatMap(p => Object.values(p))
                        }
                    })
                }
            }
        },
        methods: {
            showAddPop() {
                this.$Modal({
                    title: '添加用户', middle: true, draggable: true,
                    component: {
                        vue: addEditPop,
                        datas: {}
                    },
                    width: 750,
                    hasCloseIcon: true, fullScreen: false, middle: false, transparent: false,
                    events: {
                        reload: () => {
                            this.load()
                        }
                    }
                })
            },
            showUpdatePop(user) {
                this.$Modal({
                    title: `更新用户: ${user.name}`, middle: true, draggable: true,
                    component: {
                        vue: addEditPop,
                        datas: {user: user}
                    },
                    width: 750,
                    hasCloseIcon: true, fullScreen: false, middle: false, transparent: false,
                    events: {
                        reload: () => {
                            this.load()
                        }
                    }
                })
            },
            edit(item) {
                this.$set(item, '_edit', true);
            },
            load(page) {
                if (page == undefined || page == null) page = {page: 1};
                this.page = 1;
                this.pageSize = 10;
                this.totalRow = 0;
                this.list = [];
                $.ajax({
                    url: 'mnt/user/page',
                    data: {page: page.page || 1, kw: this.kw},
                    success: (res) => {
                        if (res.code == '00') {
                            this.page = res.data.page;
                            this.pageSize = res.data.pageSize;
                            this.totalRow = res.data.totalRow;
                            this.list = res.data.list;
                        } else this.$Notice.error(res.desc)
                    },
                    error: () => this.loading = false
                })
            }
        }
    }
</script>
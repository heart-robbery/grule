<style scoped>
    .h-taginput {
        width: 100%;
    }
</style>
<template>
    <div class="h-panel">
        <div class="h-panel-bar">
            <h-button v-if="sUser.permissionIds.find((e) => e == 'user-add')" @click="showAddPop"><i class="h-icon-plus"></i></h-button>
            <input type="text" placeholder="关键词" v-model="kw" @keyup.enter="load"/>
            <div class="h-panel-right">
                                <i class="h-split"></i>
                                <button class="h-btn h-btn-green h-btn-m" @click="load">查询</button>
<!--                <h-search placeholder="查询" v-width="200" v-model="kw" show-search-button search-text="搜索" @search="load"></h-search>-->
            </div>
        </div>
        <div class="h-panel-body">
            <div style="padding: 5px 0; margin: 0 10px;" v-for="item in list" :key="item.id">
                <p style="font-size: 15px; font-weight: bold;">
                    <span>{{item.name}}</span>
                    <span v-if="item.permissionIds.find((e) => e == 'grant')">(超级管理员)</span>
                    <span v-if="item.permissionIds.find((e) => e == 'grant-user')">(组管理员)</span>
                    &nbsp;&nbsp;&nbsp;
                    <span v-if="item.group">{{item.group}}(组)</span>
                    &nbsp;&nbsp;&nbsp;<span v-if="item.login">上次登录时间: <date-item :time="item.login" /></span>
                    &nbsp; &nbsp; <span v-if="!item._readonly" class="h-icon-edit text-hover" @click="showUpdatePop(item)"></span>
                    &nbsp; <span v-if="item._restPassword" class="h-icon-lock text-hover" @click="showResetPass(item)"></span>
                    &nbsp; <span v-if="item._deletable" class="h-icon-trash text-hover" @click="del(item)"></span>
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
    loadJs('md5')
    const addEditPop = { //添加,编辑窗口组件
        template: `
                <div style="padding-top: 10px">
                    <h-form ref="form"
                            :valid-on-change="true"
                            :show-error-tip="true"
                            :label-position="'left'"
                            :label-width="110"
                            :rules="validationRules"
                            :model="model">
                        <h-formitem label="用户名" icon="h-icon-user">
                            <input type="text" v-model="model.name" :readonly="user"/>
                        </h-formitem>
                        <h-formitem v-if="!(user == null && sUser.permissionIds.find((e) => e == 'grant-user') && !sUser.permissionIds.find((e) => e == 'grant'))" label="组名" icon="h-icon-user">
                            <input v-if="user && !sUser.permissionIds.find((e) => e == 'grant')" type="text" v-model="model.group" :readonly="true"/>
                            <h-autocomplete v-else v-model="model.group" :option="groupOpt" placeholder="组名" type="title"/>
                        </h-formitem>
                        <h-formitem v-if="!user" label="密码" icon="h-icon-lock">
                            <input type="password" v-model="model.password"/>
                        </h-formitem>
                        <h-formitem label="权限" icon="h-icon-complete">
                            <h-autocomplete v-model="model.permissions" :option="permissionOpt" type="object" :multiple="true" placeholder="权限集"/>
                        </h-formitem>
                        <h-formitem>
                                <h-button v-if="model.id" color="primary" :loading="isLoading" @click="update">提交</h-button>
                                <h-button v-else color="primary" :loading="isLoading" @click="add">提交</h-button>
                        </h-formitem>
                    </h-form>
                </div>
                `,
        props: ['user'],
        data() {
            return {
                sUser: app.$data.user,
                isLoading: false,
                option: {filterable: true},
                model: this.user ? {id: this.user.id, name: this.user.name, group: this.user.group, permissions: this.user.permissions ? this.user.permissions.map(p => {
                        let o = {enName: Object.keys(p)[0], cnName: Object.values(p)[0]};
                        return o;
                    }) : []} : {permissions: []},
                validationRules: {
                    required: ['name', 'password']
                },
                groupOpt: {
                    loadData: (filter, cb) => {
                      $.ajax({
                        url: 'mnt/user/groupPage',
                        data: {page: 1, pageSize: 5, kw: filter},
                        success: (res) => {
                          if (res.code === '00') {
                            let ls = res.data.list || [];
                            if (ls.length < 1 || !ls.filter(e => e == filter)) ls.unshift(filter);
                            cb(ls)
                          } else this.$Message.error(res.desc)
                        },
                      });
                    }
                },
                permissionOpt: {
                    keyName: 'enName', titleName: 'cnName', // minWord: 1,
                    loadData: (filter, cb) => {
                        $.ajax({
                            url: 'mnt/user/permissionPage',
                            type: 'port',
                            data: {page: 1, pageSize: 5, kw: filter, notPermissionIds: this.model.permissions.map(o => o.enName)},
                            success: (res) => {
                                if (res.code === '00') {
                                    cb(res.data.list)
                                } else this.$Message.error(res.desc)
                            },
                        });
                    }
                },
            }
        },
        mounted() {
            // this.loadPermissions()
            // this.permissionPage()
            document.onkeyup = (e) => {
                if (e.code === 'Escape') this.$emit('close');
            }
        },
        methods: {
            update() {
                this.isLoading = true;
                let data = $.extend({}, this.model);
                delete data.permissions;
                data.permissionIds = this.model.permissions.map(o => o.enName);
                $.ajax({
                    url: 'mnt/user/update',
                    type: 'post',
                    data: data,
                    success: (res) => {
                        this.isLoading = false;
                        if (res.code === '00') {
                            this.$emit('close');
                            this.$Message.success(`更新用户: ${this.model.name} 成功`);
                            this.$emit('reload');
                        } else this.$Message.error(res.desc)
                    },
                    error: () => this.isLoading = false
                })
            },
            add() {
                this.isLoading = true;
                let data = $.extend({}, this.model);
                delete data.permissions;
                data.permissionIds = this.model.permissions.map(o => o.enName);
                data.password = md5(data.password)
                $.ajax({
                    url: 'mnt/user/add',
                    type: 'post',
                    data: data,
                    success: (res) => {
                        this.isLoading = false;
                        if (res.code === '00') {
                            this.$emit('close');
                            this.$Message.success(`添加用户: ${this.model.name} 成功`);
                            this.$emit('reload');
                        } else this.$Message.error(res.desc)
                    },
                    error: (xhr, status) => {
                        this.isLoading = false
                        this.$Message.error(`${status} : ${xhr.responseText}`)
                    }
                })
            },
        }
    };
    const resetPass = { //密码重置窗口
        template: `
                <div v-width="400" style="padding-top: 10px">
                    <h-form :model="model">
                        <h-formitem label="新密码" icon="h-icon-user">
                            <input type="password" v-model="model.newPassword">
                        </h-formitem>
                        <h-formitem>
                             <h-button color="primary" :loading="isLoading" @click="restPassword">重置</h-button>
                        </h-formitem>
                    </h-form>
                </div>
                `,
        props: ['user'],
        data() {
            return {
                model: this.user ? $.extend({}, this.user) : {},
                isLoading: false,
            }
        },
        methods: {
            restPassword() {
                this.isLoading = true;
                $.ajax({
                    url: 'mnt/user/restPassword',
                    type: 'post',
                    data: {id: this.model.id, newPassword: md5(this.model.newPassword)},
                    success: (res) => {
                        this.isLoading = false;
                        if (res.code === '00') {
                            this.$emit('close');
                            this.$Message.success(`用户 ${this.model.name} 密码重置成功`);
                        } else this.$Message.error(res.desc)
                    },
                    error: (xhr, status) => {
                        this.isLoading = false
                        this.$Message.error(`${status} : ${xhr.responseText}`)
                    }
                })
            }
        }
    };
    module.exports = {
        data() {
            return {
                sUser: app.$data.user,
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
                            o.permissionIds = o.permissions.filter(o => o).flatMap(p => Object.keys(p))
                        }
                    })
                }
            }
        },
        methods: {
            del(user) {
                this.$Confirm(`删除用户: ${user.name}`, '确定删除?').then(() => {
                    this.$Message(`删除用户: ${user.name}`);
                    $.ajax({
                        url: 'mnt/user/del/' + user.id,
                        success: (res) => {
                            if (res.code === '00') {
                                this.$Message.success(`删除用户: ${user.name} 成功`);
                                this.load();
                            } else this.$Notice.error(res.desc)
                        }
                    });
                }).catch(() => {
                    this.$Message.error('取消');
                });
            },
            showAddPop() {
                this.$Modal({
                    title: '添加用户', draggable: true, closeOnMask: false,
                    component: {
                        vue: addEditPop,
                        datas: {}
                    },
                    width: 800,
                    hasCloseIcon: true, fullScreen: false, middle: true, transparent: false,
                    events: {
                        reload: () => {
                            this.load()
                        }
                    }
                })
            },
            showUpdatePop(user) {
                this.$Modal({
                    title: `更新用户: ${user.name}`, draggable: true,
                    component: {
                        vue: addEditPop,
                        datas: {user: user}
                    },
                    width: 800,
                    hasCloseIcon: true, fullScreen: false, middle: true, transparent: false,
                    events: {
                        reload: () => {
                            this.load()
                        }
                    }
                })
            },
            showResetPass(user) {
                this.$Modal({
                    title: `重置密码: ${user.name}`, draggable: true,
                    component: {
                        vue: resetPass,
                        datas: {user: user}
                    },
                    width: 400,
                    hasCloseIcon: true, fullScreen: false, middle: true, transparent: false,
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
                        if (res.code === '00') {
                            this.page = res.data.page;
                            this.pageSize = res.data.pageSize;
                            this.totalRow = res.data.totalRow;
                            this.list = res.data.list;
                        } else this.$Notice.error(res.desc)
                    },
                    error: (xhr, status) => {
                        this.loading = false
                        this.$Message.error(`${status} : ${xhr.responseText}`)
                    }
                })
            }
        }
    }
</script>
<template>
    <div class="h-panel">
        <div class="h-panel-bar">
            <h-button v-if="sUser.permissionIds.find((e) => e == 'grant' || e == 'grant')" @click="showAddPop"><i class="h-icon-plus"></i></h-button>
            <div class="h-panel-right">
                <h-search placeholder="查询" v-width="200" v-model="kw" show-search-button search-text="搜索" @search="load"></h-search>
                <!--                <i class="h-split"></i>-->
                <!--                <button class="h-btn h-btn-green h-btn-m" @click="load">查询</button>-->
            </div>
        </div>
        <div class="h-panel-body">
            <h-table ref="table" :datas="list" stripe select-when-click-tr :loading="loading" border>
                <h-tableitem title="权限标识" prop="enName" align="center"></h-tableitem>
                <h-tableitem title="权限名称" prop="cnName" align="center"></h-tableitem>
                <h-tableitem title="更新时间" align="center" :width="135">
                    <template slot-scope="{data}">
                        <date-item :time="data.updateTime" />
                    </template>
                </h-tableitem>
                <!-- <h-tableitem title="创建时间" prop="createTime" align="center"></h-tableitem> -->
                <h-tableitem title="描述" prop="comment" align="center"></h-tableitem>
                <h-tableitem v-if="sUser.permissionIds.find((e) => e == 'grant' || e == 'grant')" title="操作" align="center" :width="100">
                    <template v-if="!data.mark" slot-scope="{data}">
                        <span class="text-hover" @click="showUpdatePop(data)">编辑</span>
                        <span class="text-hover" @click="del(data)">删除</span>
                    </template>
                </h-tableitem>
                <div slot="empty">暂时无数据</div>
            </h-table>
        </div>
        <div v-if="totalRow" class="h-panel-bar">
            <h-pagination ref="pagination" :cur="page" :total="totalRow" :size="pageSize" align="right" @change="load" layout="pager,total"></h-pagination>
        </div>
    </div>
</template>
<script>
    const addEditPop = { //添加,编辑窗口组件
        template: `
                <div v-width="640" style="padding-top: 10px">
                    <h-form ref="form"
                            :valid-on-change="true"
                            :show-error-tip="true"
                            :label-position="'left'"
                            :label-width="110"
                            :rules="validationRules"
                            :model="model">
                        <h-formitem label="权限标识" icon="h-icon-complete" prop="enName">
                            <input type="text" v-model="model.enName" :readonly="permission && permission.mark"/>
                        </h-formitem>
                        <h-formitem label="权限名称" icon="h-icon-complete" prop="cnName">
                            <input type="text" v-model="model.cnName"/>
                        </h-formitem>
                        <h-formitem label="权限描述" icon="h-icon-complete" prop="comment">
                            <textarea type="text" v-model="model.comment" />
                        </h-formitem>
                        <h-formitem single>
                                <h-button v-if="model.id" color="primary" :loading="isLoading" @click="update">提交</h-button>
                                <h-button v-else color="primary" :loading="isLoading" @click="add">提交</h-button>
                                &nbsp;&nbsp;&nbsp;
                                <h-button v-if="model.id" @click="model = {}">清除</h-button>
                                <h-button v-else @click="model = {}">重置</h-button>
                            </h-formitem>
                    </h-form>
                </div>
                `,
        props: ['permission'],
        data() {
            return {
                isLoading: false,
                model: this.permission ? $.extend({}, this.permission) : {},
                validationRules: {
                    required: ['enName', 'cnName']
                },
            }
        },
        mounted() {
            document.onkeyup = (e) => {
                if (e.code === 'Escape') this.$emit('close');
            }
        },
        methods: {
            update() {
                this.isLoading = true;
                $.ajax({
                    url: 'mnt/user/updatePermission',
                    type: 'post',
                    data: this.model,
                    success: (res) => {
                        this.isLoading = false;
                        if (res.code === '00') {
                            this.$emit('close');
                            this.$Message.success(`更新: ${this.model.cnName} 成功`);
                            $.extend(this.permission, this.model);
                        } else this.$Notice.error(res.desc)
                    },
                    error: () => this.isLoading = false
                })
            },
            add() {
                this.isLoading = true;
                $.ajax({
                    url: 'mnt/user/addPermission',
                    type: 'post',
                    data: this.model,
                    success: (res) => {
                        this.isLoading = false;
                        if (res.code === '00') {
                            this.$emit('close');
                            this.$Message.success(`添加: ${this.model.cnName} 成功`);
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
                sUser: app.$data.user,
                kw: '',
                loading: false,
                page: 1, totalRow: 0, pageSize: 10, list: []
            }
        },
        mounted() {
            this.load()
        },
        methods: {
            showAddPop() {
                this.$Modal({
                    title: '添加权限', draggable: true,
                    component: {
                        vue: addEditPop,
                        datas: {}
                    },
                    width: 650,
                    hasCloseIcon: true, fullScreen: false, middle: true, transparent: false,
                    events: {
                        reload: () => {
                            this.load()
                        }
                    }
                })
            },
            showUpdatePop(permission) {
                this.$Modal({
                    title: `更新: ${permission.cnName}`, draggable: true,
                    component: {
                        vue: addEditPop,
                        datas: {permission: permission}
                    },
                    width: 650,
                    hasCloseIcon: true, fullScreen: false, middle: true, transparent: false
                })
            },
            del(permission) {
                this.$Confirm('确定删除?', `删除: ${permission.cnName}`).then(() => {
                    this.$Message(`删除: ${permission.cnName}`);
                    $.ajax({
                        url: 'mnt/user/delPermission/' + permission.id,
                        type: 'post',
                        success: (res) => {
                            if (res.code === '00') {
                                this.$Message.success(`删除: ${permission.cnName} 成功`);
                                this.load();
                            } else this.$Notice.error(res.desc)
                        }
                    });
                }).catch(() => {
                    this.$Message.error('取消');
                });
            },
            load(page) {
                if (page == undefined || page == null) page = {page: 1};
                this.loading = true;
                this.page = 1;
                this.pageSize = 10;
                this.totalRow = 0;
                this.list = [];
                $.ajax({
                    url: 'mnt/user/permissionPage',
                    data: {page: page.page || 1, kw: this.kw},
                    success: (res) => {
                        this.loading = false;
                        if (res.code === '00') {
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
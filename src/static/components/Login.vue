<style scoped>

</style>
<template>
    <h-modal v-model="opened" :close-on-mask="false" :title="'登录'">
        <div v-width="400" style="padding-top: 10px">
            <h-form ref="form"
                    :valid-on-change="validOnChange"
                    :show-error-tip="showErrorTip"
                    :label-position="labelPosition"
                    :label-width="110"
                    :rules="validationRules"
                    :model="model">
                <h-formitem label="用户名" prop="username">
                    <template v-slot:label><i class="h-icon-user"></i> 用户名</template>
                    <input type="text" v-model="model.username" @keyup.enter="submit">
                </h-formitem>
                <h-formitem label="密码" icon="h-icon-lock" prop="password">
                    <input type="password" v-model="model.password" @keyup.enter="submit">
                </h-formitem>
                <h-formitem>
                    <h-button color="primary" :loading="isLoading" @click="submit">提交</h-button>&nbsp;&nbsp;&nbsp;
                    <h-button @click="reset">重置</h-button>
                </h-formitem>
            </h-form>
        </div>
    </h-modal>
</template>
<script>
    loadJs('md5')
    module.exports = {
        data() {
            return {
                opened: true,
                isLoading: false,
                labelPosition: 'left',
                model: {username: localStorage.getItem('rule.login.username'), password: localStorage.getItem('rule.login.password')},
                validationRules: {
                    required: ['username', 'password']
                },
                showErrorTip: true,
                validOnChange: true
            }
        },
        methods: {
            submit() {
                this.isLoading = true;
                let validResult = this.$refs.form.valid();
                if (!validResult.result) {
                    // app.$Message('验证失败');
                    this.isLoading = false;
                    return
                }
                $.ajax({
                    url: 'mnt/login',
                    type: 'post',
                    data: {username: this.model.username, password: md5(this.model.password)},
                    success: (res) => {
                        this.isLoading = true;
                        if (res.code === '00') {
                            this.$Message.success('登录成功');
                            // $.extend(app.$data.user, res.data);
                            app.$data.user = res.data;
                            if (app.$data.user.permissionIds === undefined || app.$data.user.permissionIds == null) app.$data.user.permissionIds = [];
                            localStorage.setItem('rule.login.username', this.model.username);
                            localStorage.setItem('rule.login.password', this.model.password);
                        } else {
                            this.isLoading = false;
                            this.$Notice.error(res.desc)
                        }
                    },
                    error: (xhr) => {
                        this.isLoading = false;
                        if (xhr.status !== 403 && xhr.status !== 401) this.$Message.error('登陆错误. ' + xhr.status)
                    }
                })
            },
            reset() {
                this.model = {}
                //this.$refs.form.resetValid();
            }
        }
    }
</script>
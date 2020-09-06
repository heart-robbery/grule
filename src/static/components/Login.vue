<style scoped>

</style>
<template>
    <h-modal v-model="opened" :close-on-mask="false">
        <div v-width="400">
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
                <h-formitem label="密码" icon="h-icon-complete" prop="password">
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
    module.exports = {
        data: function() {
            return {
                opened: true,
                isLoading: false,
                labelPosition: 'left',
                model: {username: null, password: null},
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
                if (validResult.result) {
                    $.ajax({
                        url: 'mnt/login',
                        type: 'post',
                        data: this.model,
                        success: (res) => {
                            this.isLoading = true;
                            if (res.code == '00') {
                                app.$Message('登录成功');
                                app.$data.user = res.data
                            } else app.$Notice({type: 'error', content: res.desc, timeout: 5})
                        }
                    })
                } else {
                    // app.$Message('验证失败');
                    this.isLoading = false;
                }
            },
            reset() {
                this.$refs.form.resetValid();
            }
        }
    }
</script>
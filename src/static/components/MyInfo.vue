<template>
    <div>
        <h-form ref="form" :label-width="150" :rules="rules" :model="data">
            <h-formitem label="旧密码" prop="oldpassword">
                <input type="text" v-model="data.oldpassword"/>
            </h-formitem>
            <h-formitem label="新密码" prop="newpassword1">
                <input type="password" v-model="data.newpassword1"/>
            </h-formitem>
            <h-formitem label="再次输入新密码" prop="newpassword2">
                <input type="password" v-model="data.newpassword2" @keyup.enter="submit"/>
            </h-formitem>
            <h-formitem>
                <h-button color="primary" :loading="isLoading" @click="submit">提交</h-button>&nbsp;&nbsp;&nbsp;
                <h-button >取消</h-button>
            </h-formitem>
        </h-form>
    </div>
</template>
<script>
    module.exports = {
        data() {
            return {
                isLoading: false,
                data: {
                    oldpassword: null,
                    newpassword1: null,
                    newpassword2: null
                },
                rules: {
                    required: ['oldpassword', 'newpassword1', 'newpassword2'],
                    combineRules: [{
                        refs: ['newpassword1', 'newpassword2'],
                        valid: {
                            valid: 'equal',
                            message: '两次输入的密码不一致'
                        }
                    }]
                }
            };
        },
        methods: {
            submit() {
                let validResult = this.$refs.form.valid();
                if (!validResult.result) {
                    this.$Message.error(`还有${validResult.messages.length}个错误未通过验证`);
                    return
                }
                this.isLoading = true;
                $.ajax({
                    url: 'mnt/user/changePwd',
                    type: 'post',
                    data: {id: app.$data.user.id, newPassword: md5(this.data.newpassword2), oldPassword: md5(this.data.oldpassword)},
                    success: (res) => {
                        if (res.code === '00') {
                            this.$Message.success(`更新密码成功`);
                            this.data = {};
                            this.isLoading = false;
                        } else this.$Message.error(res.desc)
                    },
                    error: (xhr, status) => {
                        this.isLoading = false
                        this.$Message.error(`${status} : ${xhr.responseText}`)
                    }
                })
            }
        }
    }
</script>
<style scoped>

</style>
<template>
    <div>
        login
    </div>
</template>
<script>
    module.exports = {
        data: function() {
            return {
                username: null,
                password: null,
            }
        },
        methods: {
            login: function () {
                let _this = this;
                $.ajax({
                    url: 'mnt/login',
                    data: _this.data(),
                    success: function (res) {
                        if (res.code == '00') {
                            app.$data.user = res.data
                        } else app.$Notice({type: 'error', content: res.desc, timeout: 5})
                    }
                })
            }
        }
    }
</script>
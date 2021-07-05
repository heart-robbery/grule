<style scoped>

</style>
<template>
    <div>
        <h-tabs v-model="tabs.type" :datas="types"></h-tabs>
        <keep-alive>
            <component v-bind:is="tabs.type" :tabs="tabs"></component>
        </keep-alive>
    </div>
</template>
<script>
    loadJs('md5')
    module.exports = {
        data() {
            let types = {UserConfig: '用户列表'};
            if (app.$data.user.permissionIds.find((e) => e == 'grant')) types.Permission = '权限管理';
            return {
                tabs: {
                    type: localStorage.getItem('rule.userConfig.tab') || 'UserConfig',
                    showId: null
                },
                types: types,

            }
        },
        watch: {
            'tabs.type': function (v) {
                localStorage.setItem("rule.userConfig.tab", v);
            }
        }
    }
</script>
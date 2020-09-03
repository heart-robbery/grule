<style scoped>

</style>
<template>
    <div style="background: rgb(255, 255, 255); padding: 24px; min-height: 100vh;">
        =====
    </div>
</template>
<script>
    module.exports = {
        data: function() {
            return {
                headerFixed: false,
                siderFixed: false,
                siderCollapsed: false,
            };
        },
        mounted: function () {
        },
        methods: {
        },
        watch: {
            siderFixed: function() {
                if (!this.siderFixed) {
                    this.headerFixed = false;
                }
            }
        }
    };
</script>
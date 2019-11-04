<style scoped>

</style>
<template>
    <div id="tpl">
        <input type="file" @change="change">
    </div>
</template>

<script>
    module.exports = {
        data: function() {
            return {
                who: 'world'
            }
        },
        methods: {
            change: function(f) {
                console.log(f)
            }
        }
    }
</script>
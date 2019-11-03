<style>

</style>
<template>
    <div id="tpl">
        <input type="file" @change="change">
    </div>
</template>

<script>
    export default {
        method: {
            change(f) {
                console.log(f)
            }
        }
    }
</script>
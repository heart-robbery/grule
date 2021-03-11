<template>
    <div>
        <keep-alive>
            <component v-bind:is="componentKey"></component>
        </keep-alive>
    </div>
</template>
<script>
    module.exports = {
        data() {
            return {
                componentKey: 'RuleEChart'
            };
        },
        mounted() {

        },
        methods: {

        },
    }
</script>
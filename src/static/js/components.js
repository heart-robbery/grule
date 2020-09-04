// ================定义组件=====================
// 时间戳格式化组件
Vue.component('date-item', {
    props: ['time', 'format'],
    template: '<span>{{timeStr}}</span>',
    computed: {
        timeStr: function () {
            if (!this.time) return '';
            if (this.format) return moment(this.time).format(this.format);
            else return moment(this.time).format('YYYY-MM-DD HH:mm:ss')
        }
    }
});

// 异步加载组件
let m = new Map([
    ['Upload', 'Upload.vue'],
    ['Admin', 'Admin.vue'],
    ['Login', 'Login.vue'],
    ['Dashboard', 'Dashboard.vue'],
    ['PolicyCenter', 'config/PolicyCenter.vue'],
    ['DecisionConfig', 'config/DecisionConfig.vue'],
    ['PolicyConfig', 'config/PolicyConfig.vue'],
    ['RuleConfig', 'config/RuleConfig.vue'],
    ['DecisionDetail', 'config/DecisionDetail.vue'],
    ['PolicyDetail', 'config/PolicyDetail.vue'],
    ['RuleDetail', 'config/RuleDetail.vue'],
]);
m.forEach(function(value, key) {
    Vue.component(key, function (resolve, reject) {
        httpVueLoader('components/'+ value)().then(function (r) {
            resolve(r)
        })
    });
});
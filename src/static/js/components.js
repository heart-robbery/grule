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
let m = new Map();
m.set('Upload', 'Upload.vue');
m.set('Admin', 'Admin.vue');
m.set('Login', 'Login.vue');
m.set('Dashboard', 'Dashboard.vue');
m.set('PolicyCenter', 'config/PolicyCenter.vue');
m.set('DecisionConfig', 'config/DecisionConfig.vue');
m.set('PolicyConfig', 'config/PolicyConfig.vue');
m.set('RuleConfig', 'config/RuleConfig.vue');
m.set('DecisionDetail', 'config/DecisionDetail.vue');
m.set('PolicyDetail', 'config/PolicyDetail.vue');
m.set('RuleDetail', 'config/RuleDetail.vue');
m.forEach(function(value, key) {
    Vue.component(key, function (resolve, reject) {
        httpVueLoader('components/'+ value)().then(function (r) {
            resolve(r)
        })
    });
});
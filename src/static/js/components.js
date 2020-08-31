//时间戳格式化组件
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
['Upload']
    .forEach(function (item, i) {
        Vue.component(item, function (resolve, reject) {
            httpVueLoader('components/'+ item +'.vue')().then(function (r) {
                resolve(r)
            })
        });
    });

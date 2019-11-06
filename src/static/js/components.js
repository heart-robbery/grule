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
Vue.component('Upload', function (resolve, reject) {
    httpVueLoader('coms/Upload.vue')().then(function (r) {
        resolve(r)
    })
})
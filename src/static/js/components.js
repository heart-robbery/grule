var coms ={}
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
import Upload from 'coms/Upload'
Vue.component(Upload)
// Vue.component('upload', function (resolve, reject) {
//     $.ajax({
//         url: '../coms/upload.html',
//         success: function (resp) {
//             resolve($.extend({
//                 template: $(resp).find('#tpl').html(),
//             }, coms.upload))
//         }
//     })
// })
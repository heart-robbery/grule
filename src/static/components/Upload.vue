<style scoped>
    #upload {
        border: solid 1px #192e25;
        padding-left: 1vw;
    }
    #upload ul {
        list-style: none;
    }
    .file{
        position: relative;
        height: 6vh;
    }
    .file input,.file button{
        position: absolute;
        left: 0;
        top: 1vh;
        height: 4vh;
        width: 4vw;
        outline: none;
        margin-bottom: 4vh;
    }
    .file input{
        opacity: 0;
        z-index: 2;
    }
</style>

<template>
    <div id="upload">
        <div class="file">
            <input type="file" multiple @change="change"/>
            <button>点击上传</button>
        </div>

        <ul v-if="fs.length > 0">
            <li v-for="f in fs">
                <img v-if="f.data" :src="f.data" height="50px" width="60px"/>
                <span>{{f.name}}</span>
                <button @click="del(f)">删除</button>
            </li>
        </ul>
        <input v-if="!files" type="button" value="提交" @click="submit"/>
    </div>
</template>

<script>
    module.exports = {
        props: ['files'],
        data: function() {
            return {
                fs: this.files || []
            }
        },
        methods: {
            del: function(f) {
                this.fs.splice(this.fs.indexOf(f), 1)
            },
            change: function(e) {
                var t = $(e.target)
                var files = t[0].files
                for (var i = 0; i < files.length; i++) {
                    this.preReadFile(files[i], this.fs)
                }
                t.val(null) // 清空
            },
            // 回显图片
            preReadFile: function(f, fs) {
                if (f.type.indexOf('image') != -1) {
                    var reader = new FileReader();
                    reader.readAsDataURL(f);
                    reader.onload = function() {
                        f.data = reader.result;
                        fs.push(f)
                    }
                } else fs.push(f)
            },
            submit: function () {
                var _this = this
                if (_this.fs.length < 1) {
                    _this.$Notice.warning({title: '错误提示', desc: '没有文件需要上传', duration: 7})
                    return
                }
                var fd = new FormData()
                _this.fs.forEach(function (item, index) {
                    fd.append('file', item)
                })
                $.ajax({
                    timeout: 1000 * 60 * 7, // 7分钟
                    url: 'test/upload',
                    data: fd,
                    type: "POST",
                    processData: false,
                    contentType: false,
                    success: function (resp) {
                        if (resp.code == '00') {
                            _this.$Notice.success({title: '成功提示', desc: '添加成功'})
                        } else {
                            _this.$Notice.error({title: '失败提示', desc: resp.desc, duration: 7})
                        }
                    }
                })
            }
        }
    }
</script>
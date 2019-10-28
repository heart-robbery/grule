sys.name='gy'
sys.exec.corePoolSize=4

web.port=8100

jpa {
    ds {
        jdbcUrl=url='jdbc:h2:/srv/gy/h2/data'
    }
}

fileUploader{
    // 文件上传本地存放目录
    localDir='/srv/gy/upload/'
    accessUrlPrefix="http://39.104.28.131:$http.port/file"
}

log.path='/srv/gy/log'

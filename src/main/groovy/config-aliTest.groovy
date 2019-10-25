import java.time.Duration

sys.name='gy'
sys.exec.corePoolSize=4

jpa {
    ds {
        jdbcUrl=url='jdbc:h2:/srv/gy/h2/data'
    }
}

//  文件上传本地存放目录
fileUploader.localDir='/srv/gy/upload/'

log.path='/srv/gy/log'
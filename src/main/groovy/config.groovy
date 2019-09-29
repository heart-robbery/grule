import static org.slf4j.event.Level.*

sys.name='gy'
sys.exec.corePoolSize=4

dao {
    hibernate.hbm2ddl.auto='update'
    ds {
        // url='jdbc:h2:e:/tmp/h2/gy;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE'
        url='jdbc:h2:e:/tmp/h2/gy'
        username='root'
        password='root'
        minIdle=1
        maxActive=5
        validationQuery='select 1'
    }
}

//  文件上传本地存放目录
fileUploader.localDir='e:/tmp/upload/gy'

log.level.core.AppContext = WARN
log.path='e:/tmp/log/gy'
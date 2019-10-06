sys.name='gy'
sys.exec.corePoolSize=4

jpa {
    hibernate {
        hibernate.hbm2ddl.auto='update'
    }
    ds {
        // druid/dbcp2 的配置
        // url='jdbc:h2:e:/tmp/h2/gy;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE'
//        url='jdbc:h2:e:/tmp/h2/gy'
//        username='root'
//        password='root'
//        minIdle='1'
//        maxActive='5'
//        validationQuery='select 1'

        // hikari 的配置
        jdbcUrl='jdbc:h2:e:/tmp/h2/gy'
        username='root'
        password='root'
        minimumIdle=1
        maximumPoolSize=5
    }
    repo.maxPageSize=100
}

//  文件上传本地存放目录
fileUploader.localDir='e:/tmp/upload/gy'

//ep.track = ['bean.get']
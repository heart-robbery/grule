import java.time.Duration

sys.name='gy'
sys.exec.corePoolSize=4

jpa {
    hibernate {
        hibernate.hbm2ddl.auto='update'
    }
    ds {
        // url='jdbc:h2:e:/tmp/h2/gy;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE'
        jdbcUrl=url='jdbc:h2:e:/tmp/h2/gy'
        username='root'
        password='root'
        minIdle='1'
        maxActive='5'
        validationQuery='select 1'
        minimumIdle=1
        maximumPoolSize=5
    }
    repo.maxPageSize=100
}

//  文件上传本地存放目录
fileUploader.localDir='e:/tmp/upload/gy'

//ep.track = ['bean.get']


// web 配置
web {
    session.enabled=true
    // session.type='redis'
    // session 过期时间
    session.expire=Duration.ofMinutes(30)

    // 一个角色代表一组权限
    // 接口角色权限配置: level_权重数值 权重数值的权限包含小的, 平级level的角色权限互斥
    role.level_80=['role1', 'role2']
    role.level_100=['admin']
}


// redis 相关配置
redis {
    host='19.19.22.54'
    password='_@Fintell'
    database=0
}



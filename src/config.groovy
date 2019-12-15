import java.time.Duration

sys.name='gy'
sys.exec.corePoolSize=4

jpa {
    hibernate {
        hibernate.hbm2ddl.auto='update'
    }
    ds {
        // url='jdbc:h2:e:/tmp/h2/gy;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE'
        jdbcUrl=url='jdbc:h2:../h2/data'
        username='root'
        password='root'
        minimumIdle = minIdle='1'
        maximumPoolSize = maxActive='5'
        validationQuery='select 1'
    }
    repo.maxPageSize=100
}

//  文件上传本地存放目录
//fileUploader.localDir='e:/tmp/upload/'

//ep.track = ['bean.get', 'sys.started']


// web 配置
web {
    development=true
    session.enabled=true
    // session.type='redis'
    // session 过期时间
    session.expire=Duration.ofMinutes(30)

    // 一个角色代表一组权限
    // 接口角色权限配置: level_权重数值 权重数值的权限包含小的, 平级level的角色权限互斥
//    role.level_80=['role1', 'role2']
//    role.level_100=['admin']

    // 角色权限树. 注意不要循环引用
    role {
        admin = ['login', 'addUser', 'grant', 'page1', 'page2', 'page3']
        role1 = ['login', 'page1', 'role1_1', 'role1_2']
        // 多角色权限用list
        role2 = [
            [role2_1 : ['role2_1_1', 'role1_2']],
            'login', 'page2',
            'role2_2',
        ]
        role3 = ['login', role2[0], 'page3']
        role4 {
            role4_1 {
                role4_1_1 = ['role4_1_1_1']
            }
            role4_2 = ['role4_2_1_1']
        }
    }
}


// redis 相关配置
redis {
    host='19.19.22.54'
    password='_@Fintell'
    database=5
}

// 日志目录
// log.path='../log'


remoter {
    // 集群的服务中心地址
    master: 'localhost:9001'
}
tcp-server {
    // ':9001' or 'localhost:9001'
    hp=':8001'
}
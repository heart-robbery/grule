package core


import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static core.Utils.pid

class AppContext {
    static final ConfigObject env
    protected final Logger log = LoggerFactory.getLogger(AppContext.class)

    static {
        // 加载配置文件
        def cs = new ConfigSlurper();
        env = cs.parse(Class.forName('config'))
        env.merge(cs.parse(System.properties))
    }


    /**
     * start
     * @return
     */
    def start() {
        log.info("Starting Application on {} with PID {}", InetAddress.getLocalHost().getHostName(), pid())
        log.info(env.sys.name)
        log.warn(env.sys.name)
    }
}

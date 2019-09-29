package core


import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static core.Utils.pid

class AppContext {
    static final ConfigObject env
    protected final static Logger log

    static {
        // 加载配置文件
        env = new ConfigSlurper().parse(Class.forName('config'))
        log = LoggerFactory.getLogger(AppContext.class)
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

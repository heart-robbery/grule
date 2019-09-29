import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.util.FileSize

import java.nio.charset.Charset

import static ch.qos.logback.classic.Level.INFO
import static ch.qos.logback.classic.Level.WARN
import static core.AppContext.env
import static core.Utils.pid


appender("console", ConsoleAppender) {
    encoder(PatternLayoutEncoder) {
        delegate.pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%-7thread] [%-5level] [%-50.50C :%-3L] => %m%n"
        delegate.charset = Charset.forName("utf8")
    }
}


if (env.log.path) {
    appender("file", RollingFileAppender) {
        encoder(PatternLayoutEncoder) {
            delegate.pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%-7thread] [${pid()}] [%-5level] [%-50.50C :%-3L] => %m%n"
            delegate.charset = Charset.forName("utf8")
        }
        file = env.log.path + '/sys.log'
        rollingPolicy(SizeAndTimeBasedRollingPolicy) {
            delegate.fileNamePattern = "${env.log.path}/sys.%d{yyyy-MM-dd}.log.%i"
            delegate.maxFileSize = FileSize.valueOf('7MB')
            delegate.maxHistory = 100
            delegate.totalSizeCap = FileSize.valueOf('5GB')
        }
    }
}

logger("ch.qos.logback", WARN)

//env.log.level.each {k, v ->
//    logger(k.grep(), v)
//}

root(INFO, ["console"])
// root(INFO, root(INFO, ["console", "file"]))
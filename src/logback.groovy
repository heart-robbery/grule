import ch.qos.logback.classic.Level
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.util.FileSize

import java.nio.charset.Charset

import static core.Utils.pid
import static core.AppContext.env

def appenders = []
// 日志文件名
def logFileName = 'sys'
// 日志文件路径
def logPath = (env.log?.path?:'e:/tmp/log/gy')
// 去掉最后的 /
if (logPath.endsWith('/')) logPath = logPath.substring(0, logPath.length() - 2)

appender('console', ConsoleAppender) {
    appenders << 'console'
    encoder(PatternLayoutEncoder) {
        delegate.pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%-7thread] [%-5level] [%-50.50C :%-3L] => %m%n"
        delegate.charset = Charset.forName("utf8")
    }
}

if (logPath) {
    appender('file', RollingFileAppender) {
        appenders << 'file'
        encoder(PatternLayoutEncoder) {
            delegate.pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%-7thread] [${pid()}] [%-5level] [%-50.50C :%-3L] => %m%n"
            delegate.charset = Charset.forName("utf8")
        }
        file = "$logPath/${logFileName}.log"
        rollingPolicy(SizeAndTimeBasedRollingPolicy) {
            delegate.fileNamePattern = "${logPath}/${logFileName}.%d{yyyy-MM-dd}.log.%i"
            delegate.maxFileSize = FileSize.valueOf('7MB')
            delegate.maxHistory = 100
            delegate.totalSizeCap = FileSize.valueOf('5GB')
        }
    }
}


root(INFO, appenders)
logger('ch.qos.logback', WARN)
//logger('core.module.EhcacheSrv', TRACE)
//logger('org.hibernate', DEBUG)

env.log?.level?.flatten().each {String k, v ->
    if (v !instanceof Level) {
        logger(k, Level.toLevel(v?.toString()))
    } else logger(k, v)
}


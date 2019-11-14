import ch.qos.logback.classic.Level
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.util.FileSize

import java.nio.charset.Charset

import static core.AppContext.env
import static core.Utils.pid

// 日志文件名
def logFileName = 'sys'
// 日志文件路径
def logPath = env.log?.path?:''
// 去掉最后的 /
if (logPath.endsWith('/')) logPath = logPath.substring(0, logPath.length() - 1)


// 默认只输出:标准输出,文件
def appenders = env['log']?['appender']?:['console', 'file']
if (appenders instanceof String) {
    appenders = appenders.split(',').collect {it.trim()}.findAll {String s ->
        if (s) return true
        else false
    }
} else if (appenders !instanceof Collection) throw new RuntimeException('log.appenders 配置错误')


if (appenders.contains('console')) {
    appender('console', ConsoleAppender) {
        encoder(PatternLayoutEncoder) {
            delegate.pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%-7thread] [%-5level] [%-50.50C :%-3L] => %m%n"
            delegate.charset = Charset.forName("utf8")
        }
    }
}


if (logPath) { // 有日志输出目录配置
    if (appenders.contains('file')) {
        appender('file', RollingFileAppender) {
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
} else appenders.remove('file')


root(INFO, appenders)
logger('ch.qos.logback', WARN)

env.log?.level?.flatten().each {String k, v ->
    if (v !instanceof Level) {
        logger(k, Level.toLevel(v?.toString()))
    } else logger(k, v)
}


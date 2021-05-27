import ch.qos.logback.classic.Level
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.util.FileSize
import cn.xnatural.app.Utils

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

// 日志文件名
def logFileName = System.getProperty('log.file.name', 'app')

// 日志文件路径. 配了路径才会输出到文件. 默认输出到项目根目录下的log目录
def logPath = System.properties.containsKey('log.path') ? System.getProperty('log.path') : Utils.baseDir("../log").canonicalPath

// 去掉最后的 /
if ('/' != logPath && logPath.endsWith('/')) logPath = logPath.substring(0, logPath.length() - 1)

// 默认只输出:标准输出,文件
def appenders = System.getProperty('log.appenders', 'console,file')
if (appenders instanceof String) {
    appenders = appenders.split(',').collect {it.trim()}.findAll {String s ->
        if (s) return true
        else false
    }
} else if (appenders !instanceof Collection) throw new RuntimeException('log.appenders 配置错误')

if (appenders.contains('console')) { // 标准输出
    appender('console', ConsoleAppender) {
        encoder(PatternLayoutEncoder) {
            // 控制台日志格式配置
            delegate.pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%-5level] [%-7thread] [%-40.40C :%-3L] => %m%n"
            delegate.charset = Charset.forName("utf8")
        }
    }
}

if (logPath) { // 有日志输出目录配置
    if (appenders.contains('file')) { // 日志文件
        appender('file', RollingFileAppender) {
            encoder(PatternLayoutEncoder) {
                // 文件日志格式配置
                delegate.pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%-5level] [%-7thread] [%-40.40C :%-3L] => %m%n"
                delegate.charset = StandardCharsets.UTF_8
            }
            if ("/" == logPath) file = "/${logFileName}.log"
            else file = "$logPath/${logFileName}.log"
            rollingPolicy(SizeAndTimeBasedRollingPolicy) {
                if ("/" == logPath) delegate.fileNamePattern = "/${logFileName}.%d{yyyy-MM-dd}.%i.log"
                else delegate.fileNamePattern = "${logPath}/${logFileName}.%d{yyyy-MM-dd}.%i.log"
                delegate.maxFileSize = FileSize.valueOf(System.getProperty("log.maxFileSize", '50MB'))
                delegate.maxHistory = Integer.valueOf(System.getProperty("log.maxHistory", '500'))
                delegate.totalSizeCap = FileSize.valueOf(System.getProperty("log.totalSizeCap", '50GB'))
            }
        }
    }
} else appenders.remove('file')


root(Level.valueOf(System.getProperty('log.level', 'info')), appenders)

// 日志等级设置
logger('ch.qos.logback', WARN)
logger('org.hibernate.tool.hbm2ddl.SchemaUpdate', INFO)
System.properties.each {String k, v->
    if (k.startsWith("log.level.") && v) {
        logger(k.replace('log.level.', ''), Level.valueOf((String) v))
    }
}
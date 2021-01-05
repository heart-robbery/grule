package service

import cn.xnatural.enet.event.EL
import cn.xnatural.http.FileData
import core.OkHttpSrv
import core.ServerTpl
import core.Utils

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 文件上传
 */
class FileUploader extends ServerTpl {
    /**
     * 文件上传的 本地保存目录
     */
    @Lazy String localDir        = new URL('file:' + (getStr("localDir", Utils.baseDir('upload').canonicalPath))).getFile()
    /**
     * 文件上传 的访问url前缀
     */
    @Lazy URI    accessUrlPrefix = URI.create(getStr("accessUrlPrefix", ("//${ep.fire('http.hp')}/file/")) + "/").normalize()
    /**
     * 远程文件服务器url地址
     */
    @Lazy String remoteUrl       = getStr("remoteUrl", '')
    @Lazy def    http            = bean(OkHttpSrv)


    @EL(name = 'web.started', async = false)
    protected init() {
        log.info('save upload file local dir: {}', new File(localDir).canonicalPath)
        log.info('access upload file url prefix: {}', accessUrlPrefix)
        if (remoteUrl) {log.info('remote file server http url: {}', remoteUrl)}
    }


    /**
     *  返回文件名的扩展名
     * @param fileName
     * @return
     */
    static String extractFileExtension(String fileName) {
        if (!fileName) return ''
        int i = fileName.lastIndexOf(".")
        if (i == -1) return ''
        return fileName.substring(i + 1)
    }


    /**
     * 映射 文件名 到一个 url
     * @param fileName 完整的文件名
     * @return 文件访问的url
     */
    String toFullUrl(String fileName) {
        accessUrlPrefix.resolve(fileName).toString()
    }


    /**
     * 查找文件
     * @param fileName
     * @return 文件
     */
    File findFile(String fileName) { new File(localDir, fileName) }


    @EL(name = 'deleteFile')
    void delete(String fileName) {
        File f = new File(localDir, fileName)
        if (f.exists()) f.delete()
        else log.warn("delete file '{}' not exists", fileName)
    }


    /**
     * 保存文件
     * @param fd
     * @param forwardRemote
     * @return {@link FileData}
     */
    FileData save(FileData fd, boolean forwardRemote = false) { save([fd])?[0] }


    /**
     * 多文件 多线程保存
     * @param fds
     */
    // @Monitor(warnTimeOut = 7000)
    List<FileData> save(List<FileData> fds, boolean forwardRemote = false) {
        if (!fds) return fds

        // 文件流copy
        def doSave = {FileData fd ->
            if (fd == null) return fd
            if (forwardRemote) { // http上传到远程文件服务
                // 1. 阿里OSS文件服务器例子
                // def oss = new OSSClient(endpoint, accessKeyId, accessKeySecret)
                // oss.putObject('path', fd.generatedName, fd.inputStream)

                // 2. 个人http文件服务器例子
                if (remoteUrl) http?.post(remoteUrl).fileStream('file', fd.finalName, fd.inputStream).execute()
            } else {
                // 创建本地文件并写入
                def dir = new File(localDir)
                dir.mkdirs()
                fd.transferTo(dir)
                log.info('Saved file: {}, originName: {}, size: ' + fd.size, dir.canonicalPath + File.separator + fd.finalName, fd.originName)
            }
            fd
        }

        // 并发上传
        if (fds.size() >= 2) {
            ExecutorService execs
            try {
                execs = Executors.newFixedThreadPool(fds.size() - 1)
                def fs = fds.drop(1).collect {fd -> execs.submit{doSave(fd)}}.collect()
                doSave(fds[0])
                fs.each {f -> f.get()}
            } finally {
                execs?.shutdown()
            }
        } else if (fds.size() == 1){ doSave(fds[0]) }
        fds
    }
}

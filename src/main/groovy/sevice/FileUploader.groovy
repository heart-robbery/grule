package sevice

import cn.xnatural.enet.event.EL
import core.module.ServerTpl
import ctrl.common.FileData

import java.util.concurrent.Executors

class FileUploader extends ServerTpl {
    /**
     * 文件上传的 本地保存目录
     */
    private String localDir
    /**
     * 文件上传 的访问url前缀
     */
    private URI    urlPrefix


    @EL(name = "web.started", async = false)
    protected def init() {
        localDir = attrs.localDir?:new URL("file:upload").getFile()
        File dir = new File(localDir); dir.mkdirs()
        log.info("save upload file local dir: {}", dir.getAbsolutePath())

        urlPrefix = URI.create(attrs.urlPrefix?:("http://" + ep.fire("http.getHp") + "/file/") + "/").normalize()
        log.info("access upload file url prefix: {}", urlPrefix)
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
     * @return
     */
    String toFullUrl(String fileName) {
        urlPrefix.resolve(fileName).toString()
    }


    /**
     * 查找文件
     * @param fileName
     * @return
     */
    File findFile(String fileName) {
        new File(localDir + File.separator + fileName)
    }


    @EL(name = "deleteFile")
    void delete(String fileName) {
        File f = new File(localDir + File.separator + fileName)
        if (f.exists()) f.delete()
        else log.warn("delete file '{}' not exists", fileName)
    }


    /**
     * 多文件 多线程保存
     * @param fds
     */
    // @Monitor(warnTimeOut = 7000)
    def save(List<FileData> fds) {
        if (!fds) return fds

        // 文件流copy
        def doSave = {FileData fd ->
            if (fd == null) return
            // 创建文件并写入
            def f = new File(localDir + File.separator + fd.generatedName)
            f.withOutputStream {os ->
                def bs = new byte[4096]
                int n
                while (-1 != (n = fd.inputStream.read(bs))) {
                    os.write(bs, 0, n)
                }
            }
            log.info("Saved file: {}, origin name: {}", f.absolutePath, fd.originName)
            fd
        }

        // 并发上传
        if (fds.size() >= 2) {
            def execs
            try {
                execs = Executors.newFixedThreadPool(fds.size() - 1)
                def fs = fds.drop(1).collect {fd -> execs.submit(doSave(fd))}.collect()
                doSave(fds[0])
                fs.each {f -> f.get()}
            } finally {
                execs?.shutdown()
            }
        } else if (fds.size() == 1){ doSave(fds[0]) }
        fds
    }


    String getLocalDir() {localDir}
}

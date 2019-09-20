package sevice

import cn.xnatural.enet.event.EL
import cn.xnatural.enet.server.ServerTpl
import ctrl.common.FileData
import org.apache.commons.io.IOUtils

import java.util.concurrent.Executors

class FileUploader extends ServerTpl {
    /**
     * 文件上传的 本地保存目录
     */
    private String localDir;
    /**
     * 文件上传 的访问url前缀
     */
    private URI    urlPrefix;


    FileUploader() { super("file-uploader"); }


    @EL(name = "web.started")
    protected void init() {
        attrs.putAll((Map<? extends String, ?>) ep.fire("env.ns", getName()));
        try {
            localDir = getStr("local-dir", new URL("file:upload").getFile());
            File dir = new File(localDir); dir.mkdirs();
            log.info("save upload file local dir: {}", dir.getAbsolutePath());

            urlPrefix = URI.create(getStr("url-prefix", ("http://" + ep.fire("http.getHp") + "/file/")) + "/").normalize();
            log.info("access upload file url prefix: {}", urlPrefix);
        } catch (MalformedURLException e) {
            log.error(e);
        }
    }


    /**
     *  例: 文件名为 aa.txt, 返回: arr[0]=aa, arr[1]=txt
     * @param fileName
     * @return
     */
    static String[] extractFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) return [null, null];
        int i = fileName.lastIndexOf(".");
        if (i == -1) return [fileName, null];
        return [fileName.substring(0, i), fileName.substring(i + 1)];
    }


    /**
     * 映射 文件名 到一个 url
     * @param fileName
     * @return
     */
    String toFullUrl(String fileName) {
        return urlPrefix.resolve(fileName).toString();
    }


    /**
     * 查找文件
     * @param fileName
     * @return
     */
    File findFile(String fileName) {
        return new File(localDir + File.separator + fileName);
    }


    @EL(name = "deleteFile")
    void delete(String fileName) {
        File f = new File(localDir + File.separator + fileName);
        if (f.exists()) f.delete();
        else log.warn("delete file '${fileName}' not exists");
    }


    /**
     * 多文件 多线程保存
     * @param fds
     */
    // @Monitor(warnTimeOut = 7000)
    def save(List<FileData> fds) {
        if (fds == null || fds.isEmpty()) return fds;

        // 文件流copy
        def doSave = {FileData fd ->
            if (fd == null) return
            // 创建文件并写入
            def f = new File(localDir + File.separator + fd.getResultName());
            f.withDataOutputStream {IOUtils.copy(fd.inputStream, it)}
            log.info("Saved file: $f.absolutePath, origin name: ${fd.originName}")
            return fd
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

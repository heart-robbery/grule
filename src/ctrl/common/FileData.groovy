package ctrl.common

import groovy.transform.ToString
import service.FileUploader

@ToString(excludes = 'inputStream', includePackage = false)
class FileData {
    /**
     * 原始文件名(包含扩展名)
     */
    String                originName
    /**
     * 文件最终名(系统生成唯一文件名(包含扩展名))
     */
    String                finalName
    /**
     * 文件流
     */
    transient InputStream inputStream
    /**
     * 大小
     */
    Long                  size


    FileData setOriginName(String fName) {
        this.originName = fName
        def extension = FileUploader.extractFileExtension(fName)
        def id = UUID.randomUUID().toString().replace('-', '')
        finalName = (extension ? (id + '.' + extension) : id)
        this
    }

}

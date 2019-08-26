package rest

import org.apache.commons.lang3.StringUtils
import sevice.FileUploader

class FileData {
    /**
     * 扩展名
     */
    String      extension;
    /**
     * 原始文件名(包含扩展名)
     */
    String      originName;
    /**
     * 系统生成的唯一名(不包含扩展名)
     */
    String      generatedName;
    /**
     * 文件流
     */
    transient InputStream inputStream;
    /**
     * 大小
     */
    Long        size;

    String getResultName() {
        if (!generatedName) generatedName = UUID.randomUUID().toString().replace('-', '')
        return (StringUtils.isEmpty(getExtension()) ? getGeneratedName() : (getGeneratedName() + "." + getExtension()));
    }


    FileData setOriginName(String fName) {
        this.originName = fName
        def arr = FileUploader.extractFileName(fName)
        if (arr && arr.length > 1) extension = arr[1]
        return this;
    }

}

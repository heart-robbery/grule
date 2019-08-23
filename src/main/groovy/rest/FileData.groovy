package rest

import org.apache.commons.lang3.StringUtils

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
        return (StringUtils.isEmpty(getExtension()) ? getGeneratedName() : (getGeneratedName() + "." + getExtension()));
    }
}

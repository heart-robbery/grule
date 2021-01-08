package entity

import javax.persistence.Entity

@Entity
class VersionFile extends UploadFile {
    // 1.0.1
    String version
    // 用于比较大小
    Integer versionNum
}

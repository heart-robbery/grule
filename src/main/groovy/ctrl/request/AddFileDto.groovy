package ctrl.request

import ctrl.common.BasePojo
import ctrl.common.FileData

class AddFileDto extends BasePojo {
    String   name;
    Integer  age;
    FileData headportrait;
}

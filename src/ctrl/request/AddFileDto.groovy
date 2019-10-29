package ctrl.request


import ctrl.common.FileData
import groovy.transform.ToString

@ToString
class AddFileDto {
    String   name;
    Integer  age;
    FileData headportrait;
}

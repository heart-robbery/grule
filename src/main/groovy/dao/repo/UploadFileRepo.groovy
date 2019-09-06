package dao.repo

import cn.xnatural.enet.server.dao.hibernate.BaseRepo
import cn.xnatural.enet.server.dao.hibernate.Repo
import dao.entity.UploadFile

@Repo
class UploadFileRepo extends BaseRepo<UploadFile, Long> {

    @Override
    <S extends UploadFile> S saveOrUpdate(S e) {
        Date d = new Date();
        if (e.getCreateTime() == null) e.setCreateTime(d);
        if (e.getUpdateTime() == null) e.setCreateTime(d);
        return super.saveOrUpdate(e);
    }
}

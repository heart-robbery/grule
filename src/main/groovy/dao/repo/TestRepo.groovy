package dao.repo

import cn.xnatural.enet.server.dao.hibernate.BaseRepo
import cn.xnatural.enet.server.dao.hibernate.Repo
import dao.entity.Test

@Repo
class TestRepo extends BaseRepo<Test, Long> {
}

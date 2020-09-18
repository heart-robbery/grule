package service.rule

import cn.xnatural.enet.event.EL
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature
import core.ServerTpl
import core.Utils
import core.jpa.BaseRepo
import core.jpa.IEntity
import dao.entity.OpHistory

import java.text.SimpleDateFormat

/**
 * 记录实体的修改历史
 */
class OpHistorySrv extends ServerTpl {

    @Lazy def repo = bean(BaseRepo, 'jpa_rule_repo')


    /**
     * 操作历史
     * @param entity 实体
     */
    @EL(name = 'enHistory', async = true)
    void enHistory(IEntity entity, String operator) {
        def data = Utils.toMapper(entity)
            .addConverter('createTime', {v -> if (v instanceof Date) new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS").format(v) else v})
            .addConverter('updateTime', {v -> if (v instanceof Date) new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS").format(v) else v})
            .build()
        repo.saveOrUpdate(
            new OpHistory(tbName: repo.tbName(entity.class), operator: operator, content: JSON.toJSONString(data, SerializerFeature.DisableCircularReferenceDetect))
        )
    }
}

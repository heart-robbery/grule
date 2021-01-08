package service.rule

import cn.xnatural.app.ServerTpl
import cn.xnatural.app.Utils
import cn.xnatural.enet.event.EL
import cn.xnatural.jpa.IEntity
import cn.xnatural.jpa.Repo
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.serializer.SerializerFeature
import entity.OpHistory

import java.text.SimpleDateFormat

/**
 * 记录实体的修改历史
 */
class OpHistorySrv extends ServerTpl {

    @Lazy def repo = bean(Repo, 'jpa_rule_repo')


    /**
     * 操作历史
     * @param entity 实体
     */
    @EL(name = 'enHistory', async = true)
    void enHistory(IEntity entity, String operator) {
        def data = Utils.toMapper(entity).ignore("metaClass")
            .addConverter('createTime', {v -> if (v instanceof Date) new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS").format(v) else v})
            .addConverter('updateTime', {v -> if (v instanceof Date) new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS").format(v) else v})
            .build()
        repo.saveOrUpdate(
            new OpHistory(tbName: repo.tbName(entity.class), operator: operator, content: JSON.toJSONString(data, SerializerFeature.WriteMapNullValue, SerializerFeature.DisableCircularReferenceDetect))
        )
    }
}

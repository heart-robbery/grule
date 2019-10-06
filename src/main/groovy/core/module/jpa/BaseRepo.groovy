package core.module.jpa


import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.Transaction
import org.hibernate.metamodel.spi.MetamodelImplementor
import org.hibernate.persister.entity.AbstractEntityPersister
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.annotation.Resource
import javax.persistence.NoResultException
import javax.persistence.Query
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Predicate
import javax.persistence.criteria.Root
import java.util.function.Function


class BaseRepo {
    protected Logger log = LoggerFactory.getLogger(getClass())
    @Resource
    protected SessionFactory sf
    protected ConfigObject attrs


    static ThreadLocal<Boolean> txFlag = ThreadLocal.withInitial({false})
    /**
     * 事务执行方法
     * @param fn
     * @param okFn
     * @param failFn
     * @return
     */
    def <T> T trans(Function<Session, T> fn, Runnable okFn = null, failFn = null) {
        Session s = sf.getCurrentSession()
        Transaction tx = s.getTransaction()
        // 当前线程是否有事务存在
        if (txFlag.get()) return fn.apply(s)
        else { // 当前线程没有事务则开启新事务
            tx.begin(); txFlag.set(true)
            Throwable ex = null
            try {
                def r = fn.apply(s); tx.commit(); s.close(); txFlag.set(false)
                return r
            } catch (Throwable t) {
                tx.rollback(); ex = t; s.close(); txFlag.set(false);
                throw t
            } finally {
                if (ex) {
                    if (failFn != null) failFn.accept(ex);
                } else {// 成功
                    if (okFn != null) okFn.run()
                }
            }
        }
    }

    /**
     * 根据实体类, 查表名字
     * @param eType
     * @return
     */
    String tbName(Class<IEntity> eType) {
        ((AbstractEntityPersister) ((MetamodelImplementor) sf.getMetamodel()).locateEntityPersister(eType)).getRootTableName()
    }


    def <E extends IEntity> E saveOrUpdate(E e) {
        trans{ s ->
            if (e instanceof BaseEntity) {
                def d = new Date()
                if (!e.createTime) e.createTime = d
                e.updateTime = d
            }
            s.saveOrUpdate(e)
            e
        }
    }
    
    
    def <T extends IEntity> T findById(Class<T> eType, Serializable id) {
        if (eType == null) throw new IllegalArgumentException('eType must not be null')
        trans{ s -> s.get(eType, id)}
    }


    def findOne(Query query) {
        trans{
            try {
                return query.setMaxResults(1).getSingleResult()
            } catch (NoResultException e) {}
            null
        }
    }

    
    /**
     * 删实体
     * @param e
     */
    def <E extends IEntity> void delete(E e) { trans{ s -> s.delete(e)} }
    
    
    def <E extends IEntity> boolean delete(Class<E> eType, Serializable id) {
        if (eType == null) throw new IllegalArgumentException('eType must not be null')
        trans{ s ->
            // NOTE: 被删除的实体主键名必须为 "id";
            s.createQuery("delete from $eType.simpleName where id=:id")
                .setParameter("id", id)
                .executeUpdate() > 0
        }
    }


    /**
     * 分页查询
     * @param eType
     * @param pageIndex
     * @param pageSize
     * @param spec
     * @return
     */
    def <E extends IEntity> Page<E> findPage(Class<E> eType, Integer pageIndex, Integer pageSize, Specification spec) {
        if (eType == null) throw new IllegalArgumentException('eType must not be null')
        trans{s ->
            CriteriaBuilder cb = s.getCriteriaBuilder()
            CriteriaQuery<E> query = cb.createQuery(eType)
            Root<E> root = query.from(eType)
            Object p = spec?.toPredicate(root, query, cb)
            if (p instanceof Predicate) query.where(p)
            int ps = (pageSize == null ? attrs.defaultPageSize : (pageSize > attrs.maxPageSize ? attrs.defaultPageSize : pageSize))
            int pi = pageIndex?:0
            new Page<E>(
                pageIndex: pi, pageSize: ps,
                list: s.createQuery(query).setFirstResult(pi * ps).setMaxResults(ps).list(),
                totalRow: count(eType, spec)
            )
        }
    }

    /**
     * 根据实体类, 统计
     * @param eType
     * @param spec
     * @return
     */
    def <E extends IEntity> long count(Class<E> eType, Specification spec = null) {
        if (eType == null) throw new IllegalArgumentException('eType must not be null')
        trans{ s ->
            CriteriaBuilder cb = s.getCriteriaBuilder()
            CriteriaQuery<Long> query = cb.createQuery(Long.class)
            Root<E> root = query.from(eType)
            Object p = spec?.toPredicate(root, query, cb)
            if (query.isDistinct()) query.select(cb.countDistinct(root))
            else query.select(cb.count(root))
            query.orderBy(Collections.emptyList())
            if (p instanceof Predicate) query.where(p)
            s.createQuery(query).getSingleResult()
        }
    }
}

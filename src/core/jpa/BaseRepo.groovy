package core.jpa

import core.Page
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.Transaction
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider
import org.hibernate.metamodel.spi.MetamodelImplementor
import org.hibernate.persister.entity.AbstractEntityPersister
import org.hibernate.service.ServiceRegistry
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Predicate
import javax.persistence.criteria.Root
import javax.sql.DataSource
import java.util.function.Consumer
import java.util.function.Function

/**
 * hibernate 基本操作方法集
 */
class BaseRepo {
    protected static final Logger         log = LoggerFactory.getLogger(BaseRepo)
    protected final SessionFactory sf
    protected final Map attr

    // 传过来 不能注入(@Resource注入多数据源有问题)
    BaseRepo(SessionFactory sf, Map attr = [:]) {
        if (sf == null) throw new NullPointerException("SessionFactory must not be null")
        this.sf = sf
        this.attr = attr
    }


    // 事务的线程标记
    static final ThreadLocal<Boolean> txFlag = ThreadLocal.withInitial({false})
    /**
     * 事务执行方法
     * @param fn
     * @param okFn
     * @param failFn
     * @return
     */
    def <T> T trans(Function<Session, T> fn, Runnable okFn = null, Consumer<Exception> failFn = null) {
        Session s = sf.getCurrentSession()
        // 当前线程存在事务
        if (txFlag.get()) return fn.apply(s)
        else { // 当前线程没有事务,开启新事务
            Transaction tx = s.getTransaction()
            tx.begin(); txFlag.set(true)
            Exception ex = null
            try {
                def r = fn.apply(s); tx.commit(); txFlag.set(false); s.close()
                return r
            } catch (t) {
                tx.rollback(); txFlag.set(false); ex = t; s.close()
                throw t
            } finally {
                if (ex) {
                    if (failFn != null) failFn.accept(ex)
                } else {// 成功
                    if (okFn != null) okFn.run()
                }
            }
        }
    }


    /**
     * 根据实体类, 查表名字
     * @param eType
     * @return 表名
     */
    String tbName(Class<IEntity> eType) {
        ((AbstractEntityPersister) ((MetamodelImplementor) sf.getMetamodel()).locateEntityPersister(eType)).getRootTableName()
    }

    /**
     * 根据实体名,查表名
     * @param eName 实体名
     * @return 表名
     */
    String tbName(String eName) {
        ((AbstractEntityPersister) ((MetamodelImplementor) sf.getMetamodel()).locateEntityPersister(eName)).getRootTableName()
    }


    /**
     * 连接mysql当前数据库的库名
     * @return 数据库名
     */
    String getDbName() { sf['jdbcServices']?['jdbcEnvironment']?['currentCatalog']?['text'] }


    /**
     * 连接 jdbcUrl
     * @return 连接 jdbcUrl
     */
    String getJdbcUrl() {
        DataSource ds = ((ServiceRegistry) sf['serviceRegistry'])?.getService(ConnectionProvider)?['dataSource']
        if (ds) return ds['jdbcUrl']?:ds['url']
        null
    }


    /**
     * 保存/更新实体
     * @param e
     * @return
     */
    def <E extends IEntity> E saveOrUpdate(E e) {
        trans{s ->
            if (e instanceof BaseEntity) {
                def d = new Date()
                if (e.createTime == null) e.createTime = d
                e.updateTime = d
            }
            s.saveOrUpdate(e)
            e
        }
    }


    /**
     * 根据id查找实体
     * @param eType
     * @param id
     * @return
     */
    def <T extends IEntity> T findById(Class<T> eType, Serializable id) {
        if (eType == null) throw new IllegalArgumentException("eType must not be null")
        trans{s -> s.get(eType, id)}
    }


    /**
     * 查询
     * @param eType 实体类型
     * @param spec 条件
     * @return <T> 实体对象
     */
    def <T extends IEntity> T find(Class<T> eType, CriteriaSpec spec) {
        trans{s ->
            CriteriaBuilder cb = s.getCriteriaBuilder()
            CriteriaQuery<T> query = cb.createQuery(eType)
            Root<T> root = query.from(eType)
            Object p = spec?.toPredicate(root, query, cb)
            if (p instanceof Predicate) query.where(p)
            List<T> ls = s.createQuery(query).setMaxResults(1).list()
            return (ls.size() == 1 ? ls.get(0) : null)
        } as T
    }


    /**
     * 删实体
     * @param e
     */
    def <E extends IEntity> void delete(E e) { trans{ s -> s.delete(e)} }


    /**
     * 根据id删除
     * @param eType
     * @param id
     * @return true: 删除成功
     */
    def <E extends IEntity> boolean delete(Class<E> eType, Serializable id) {
        if (eType == null) throw new IllegalArgumentException("eType must not be null")
        trans{s ->
            // NOTE: 被删除的实体主键名必须为 "id"
            s.createQuery("delete from $eType.simpleName where id=:id")
                .setParameter('id', id)
                .executeUpdate() > 0
        }
    }


    /**
     * 查询多条数据
     * @param eType 实体类型
     * @param spec 条件
     * @return
     */
    def <E extends IEntity> List<E> findList(Class<E> eType, CriteriaSpec spec) {
        findList(eType, null, null, spec)
    }


    /**
     * 查询多条数据
     * @param eType 实体类型
     * @param start 开始行 从0开始
     * @param limit 条数限制
     * @param spec 条件
     * @return list
     */
    def <E extends IEntity> List<E> findList(Class<E> eType, Integer start = null, Integer limit = null, CriteriaSpec spec = null) {
        if (eType == null) throw new IllegalArgumentException("eType must not be null")
        if (start != null && start < 0) throw new IllegalArgumentException("start must >= 0 or not give")
        if (limit != null && limit <= 0) throw new IllegalArgumentException("limit must > 0 or not give")
        return trans(s -> {
            CriteriaBuilder cb = s.getCriteriaBuilder()
            CriteriaQuery<E> cQuery = cb.createQuery(eType)
            Root<E> root = cQuery.from(eType)
            Object p = spec == null? null :spec.toPredicate(root, cQuery, cb)
            if (p instanceof Predicate) cQuery.where((Predicate) p)
            def query = s.createQuery(cQuery)
            if (start) query.setFirstResult(start)
            if (limit) query.setMaxResults(limit)
            return query.list()
        })
    }


    /**
     * 分页查询
     * @param eType
     * @param page 从1开始
     * @param pageSize
     * @param spec
     * @return
     */
    def <E extends IEntity> Page<E> findPage(Class<E> eType, Integer page, Integer pageSize, CriteriaSpec spec = null) {
        if (eType == null) throw new IllegalArgumentException("eType must not be null")
        if (page == null || page < 1) throw new IllegalArgumentException("page: $page, must >=1")
        if (pageSize == null || pageSize < 1) throw new IllegalArgumentException("pageSize: $pageSize, must >=1")
        trans{s ->
            CriteriaBuilder cb = s.getCriteriaBuilder()
            CriteriaQuery<E> query = cb.createQuery(eType)
            Root<E> root = query.from(eType)
            def p = spec?.toPredicate(root, query, cb)
            if (p instanceof Predicate) query.where(p)
            new Page<E>(
                page: page, pageSize: pageSize,
                list: s.createQuery(query).setFirstResult((page - 1) * pageSize).setMaxResults(pageSize).list(),
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
    def <E extends IEntity> long count(Class<E> eType, CriteriaSpec spec = null) {
        if (eType == null) throw new IllegalArgumentException("eType must not be null")
        trans{s ->
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


    /**
     * Criteria 查询 spec
     * @param <E>
     */
    interface CriteriaSpec<E> {
        abstract def toPredicate(Root<E> root, CriteriaQuery<?> query, CriteriaBuilder cb)
    }
}

package core.module.jpa

import cn.xnatural.enet.event.EL
import cn.xnatural.enet.event.EP
import core.Utils
import core.module.ServerTpl
import org.hibernate.SessionFactory
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.engine.jdbc.connections.internal.DatasourceConnectionProviderImpl
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider
import org.hibernate.jpa.HibernatePersistenceProvider

import javax.persistence.SharedCacheMode
import javax.persistence.ValidationMode
import javax.persistence.spi.ClassTransformer
import javax.persistence.spi.PersistenceUnitInfo
import javax.persistence.spi.PersistenceUnitTransactionType
import javax.sql.DataSource

import static javax.persistence.SharedCacheMode.UNSPECIFIED

class HibernateSrv extends ServerTpl {
    SessionFactory        sf
    BaseRepo              repo
    protected DataSource  ds
    protected List<Class> entities = new LinkedList<>()
    
    
    HibernateSrv() { super('jpa') }
    HibernateSrv(String name) { super(name) }


    @EL(name = 'sys.starting')
    def start() {
        if (sf) throw new RuntimeException("$name is already running")
        if (ep == null) {ep = new EP(); ep.addListenerSource(this)}

        // 创建 hibernate
        Map props = new Properties()
        props.put("hibernate.hbm2ddl.auto", "none")
        props.put("hibernate.physical_naming_strategy", PhysicalNaming.class.name)
        props.put("hibernate.implicit_naming_strategy", ImplicitNaming.class.name)
        props.put("hibernate.current_session_context_class", "thread")
        props.put("hibernate.temp.use_jdbc_metadata_defaults", 'true')
        props.putAll(attrs.hibernate.flatten())

        initDataSource()
        MetadataSources ms = new MetadataSources(
            new StandardServiceRegistryBuilder().addService(ConnectionProvider.class, new DatasourceConnectionProviderImpl(dataSource: ds, available: true)).applySettings(props).build()
        )
        ms.annotatedClasses.addAll(entities)
        sf = ms.buildMetadata().buildSessionFactory()
        exposeBean(sf, ['sessionFactory', 'entityManagerFactory'])

        // 创建hibernate的公共查询
        repo = new BaseRepo(); repo.attrs = attrs.repo; ep.fire('inject', repo)
        if (!repo.attrs.containsKey('defaultPageSize')) repo.attrs.defaultPageSize = 15
        if (!repo.attrs.containsKey('maxPageSize')) repo.attrs.maxPageSize = 30
        exposeBean(repo)

        log.info("Created {}(Hibernate) client", name)
        ep.fire("${name}.started")
    }


    @EL(name = 'sys.stopping')
    def stop() {
        log.debug("Shutdown '{}(Hibernate)' Client", name)
        ds?.invokeMethod('close', null); ds = null
        sf?.close(); sf = null
    }


    /**
     * 添加被管理的实体类
     * @param clzs
     * @return
     */
    def entities(Class... clzs) {
        if (!clzs) return this
        clzs.each {entities.add(it)}
        this
    }


    /**
     * 初始化数据源
     * @return
     */
    protected def initDataSource() {
        if (ds) throw new RuntimeException('DataSource already exist')
        Map<String, Object> dsAttr = attrs.ds

        log.debug("Create dataSource properties: {}", dsAttr)

        // Hikari 数据源
        try {
            ds = (DataSource) Class.forName('com.zaxxer.hikari.HikariDataSource').newInstance()
            dsAttr.each {k, v -> if (ds.hasProperty(k)) ds.(k) = v}
        } catch (ClassNotFoundException ex) {}

        dsAttr.put("validationQuery", "select 1")
        // druid 数据源
        if (!ds) {
            try {
                ds = (DataSource) Utils.findMethod(Class.forName("com.alibaba.druid.pool.DruidDataSourceFactory"), "createDataSource", Map.class).invoke(null, dsAttr)
            } catch(ClassNotFoundException ex) {}
        }

        // dbcp2 数据源
        if (!ds) {
            try {
                Properties p = new Properties(); p.putAll(dsAttr)
                ds = (DataSource) Utils.findMethod(Class.forName("org.apache.commons.dbcp2.BasicDataSourceFactory"), "createDataSource", Properties.class).invoke(null, dsAttr)
            } catch (ClassNotFoundException ex) {}
        }
        if (!ds) throw new RuntimeException("Not found DataSource implement class")
        log.debug("Created datasource for {} Server. {}", name, ds)
    }
}

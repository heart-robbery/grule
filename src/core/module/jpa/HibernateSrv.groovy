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

import javax.sql.DataSource

class HibernateSrv extends ServerTpl {
    SessionFactory        sf
    protected DataSource  ds
    @Lazy protected List<Class> entities = new LinkedList<>()
    
    
    HibernateSrv() { super('jpa') }
    HibernateSrv(String name) { super(name) }


    @EL(name = 'sys.starting', async = true)
    def start() {
        if (sf) throw new RuntimeException("$name is already running")
        if (ep == null) {ep = new EP(); ep.addListenerSource(this)}

        // 创建 hibernate
        Map props = new Properties()
        props.put('hibernate.hbm2ddl.auto', 'none')
        props.put('hibernate.physical_naming_strategy', PhysicalNaming.class.name)
        props.put('hibernate.implicit_naming_strategy', ImplicitNaming.class.name)
        props.put('hibernate.current_session_context_class', 'thread')
        props.put('hibernate.temp.use_jdbc_metadata_defaults', 'true') // 自动探测连接的数据库信息,该用哪个Dialect
        props.putAll(attrs()['hibernate'].flatten())

        initDataSource()
        MetadataSources ms = new MetadataSources(
            new StandardServiceRegistryBuilder()
                .addService(ConnectionProvider.class, new DatasourceConnectionProviderImpl(dataSource: ds, available: true))
                .applySettings(props).build()
        )
        entities.each {ms.addAnnotatedClass(it)}
        sf = ms.buildMetadata().buildSessionFactory()
        exposeBean(sf, ["${name}_sessionFactory", "${name}_entityManagerFactory"])

        // 创建hibernate的公共查询
        BaseRepo repo = new BaseRepo(sf) {Map attrs() { HibernateSrv.this.attrs()['repo'] }}
        ep.fire('inject', repo)
        exposeBean(repo, ["${name}_repo"])

        log.info('Created {}(Hibernate) client', name)
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
    protected initDataSource() {
        if (ds) throw new RuntimeException('DataSource already exist')
        Map<String, Object> dsAttr = attrs().ds
        log.debug('Create dataSource properties: {}', dsAttr)

        // Hikari 数据源
        try {
            ds = (DataSource) Class.forName('com.zaxxer.hikari.HikariDataSource').newInstance()
            dsAttr.each {k, v ->
                if (ds.hasProperty(k)) {
                    def t = ds[(k)].getClass()
                    if (t == Integer || t == int) ds.(k) = Integer.valueOf(v)
                    else if (t == Long || t == long) ds.(k) = Long.valueOf(v)
                    else if (t == Boolean || t == boolean) ds.(k) = Boolean.valueOf(v)
                    else ds.(k) = v
                }
            }
        } catch (ClassNotFoundException ex) {}

        // druid 数据源
        if (!ds) {
            try {
                Map props = new HashMap()
                dsAttr.each {props.put(it.key, Objects.toString(it.value, ''))}
                if (!props.containsKey('validationQuery')) props.put('validationQuery', 'select 1')
                if (!props.containsKey('filters')) { // 默认监控慢sql
                    props.put('filters', 'stat')
                    props.put('connectionProperties', 'druid.stat.slowSqlMillis=5000')
                }
                ds = (DataSource) Utils.findMethod(Class.forName('com.alibaba.druid.pool.DruidDataSourceFactory'), 'createDataSource', Map.class).invoke(null, props)
            } catch(ClassNotFoundException ex) {}
        }

        // dbcp2 数据源
        if (!ds) {
            try {
                Map props = new HashMap()
                dsAttr.each {props.put(it.key, Objects.toString(it.value, ''))}
                if (!props.containsKey('validationQuery')) props.put('validationQuery', 'select 1')
                ds = (DataSource) Utils.findMethod(Class.forName('org.apache.commons.dbcp2.BasicDataSourceFactory'), 'createDataSource', Properties.class).invoke(null, props)
            } catch (ClassNotFoundException ex) {}
        }
        if (!ds) throw new RuntimeException('Not found DataSource implement class')
        log.debug('Created datasource for {} Server. {}', name, ds)
    }
}

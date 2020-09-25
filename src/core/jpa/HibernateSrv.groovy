package core.jpa

import cn.xnatural.enet.event.EL
import cn.xnatural.enet.event.EP
import core.ServerTpl
import core.Utils
import org.hibernate.SessionFactory
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.engine.jdbc.connections.internal.DatasourceConnectionProviderImpl
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider

import javax.sql.DataSource

class HibernateSrv extends ServerTpl {
    protected SessionFactory    sf
    protected DataSource        datasource
    @Lazy protected List<Class> entities = new LinkedList<>()
    
    
    HibernateSrv() { super('jpa') }
    HibernateSrv(String name) { super(name) }


    @EL(name = 'sys.starting', async = true)
    void start() {
        if (sf) throw new RuntimeException("$name is already running")
        if (ep == null) {ep = new EP(); ep.addListenerSource(this)}

        // 创建 hibernate
        final Map props = new HashMap()
        // 可配置的属性名 AvailableSettings
        props.put('hibernate.hbm2ddl.auto', 'none')
        props.put('hibernate.physical_naming_strategy', PhysicalNaming)
        props.put('hibernate.implicit_naming_strategy', ImplicitNaming)
        props.put('hibernate.current_session_context_class', 'thread')
        props.put('hibernate.temp.use_jdbc_metadata_defaults', 'true') // 自动探测连接的数据库信息,该用哪个Dialect
        props.putAll(attrs('hibernate').flatten())

        initDataSource()
        // exposeBean(datasource, ["${name}_datasource"])
        // 创建 SessionFactory
        MetadataSources ms = new MetadataSources(
            new StandardServiceRegistryBuilder()
                .addService(ConnectionProvider.class, new DatasourceConnectionProviderImpl(dataSource: datasource, available: true))
                .applySettings(props).build()
        )
        entities.each {ms.addAnnotatedClass(it)}
        sf = ms.buildMetadata().buildSessionFactory()
        exposeBean(sf, ["${name}_sessionFactory", "${name}_entityManagerFactory"])

        // 创建hibernate的公共查询
        BaseRepo repo = new BaseRepo(sf, attrs('repo'))
        exposeBean(repo, ["${name}_repo"])

        log.info('Created {}(Hibernate) client', name)
        ep.fire("${name}.started")
    }


    @EL(name = 'sys.stopping')
    void stop() {
        log.debug("Shutdown '{}(Hibernate)' Client", name)
        datasource?.invokeMethod('close', null); datasource = null
        sf?.close(); sf = null
    }


    /**
     * 添加被管理的实体类
     * @param clzs
     * @return
     */
    HibernateSrv entities(Class... clzs) {
        if (!clzs) return this
        clzs.each {entities.add(it)}
        this
    }


    /**
     * 初始化数据源
     * @return
     */
    protected void initDataSource() {
        if (datasource) throw new RuntimeException('DataSource already exist')
        Map<String, Object> dsAttr = attrs('datasource')
        log.debug('Create dataSource properties: {}', dsAttr)
        datasource = createDs(dsAttr)
        if (!datasource) throw new RuntimeException('Not found DataSource implement class')
        log.debug('Created datasource for {} Server. {}', name, datasource)
    }


    /**
     * 创建一个DataSource
     * @param dsAttr
     * @return
     */
    static DataSource createDs(Map<String, Object> dsAttr) {
        DataSource db
        // druid 数据源
        try {
            Map props = new HashMap()
            dsAttr.each {props.put(it.key, Objects.toString(it.value, ''))}
            // if (!props.containsKey('validationQuery')) props.put('validationQuery', 'select 1') // oracle
            if (!props.containsKey('filters')) { // 默认监控慢sql
                props.put('filters', 'stat')
            }
            if (!props.containsKey('connectionProperties')) {
                // com.alibaba.druid.filter.stat.StatFilter
                props.put('connectionProperties', 'druid.stat.logSlowSql=true;druid.stat.slowSqlMillis=5000')
            }
            db = (DataSource) Utils.findMethod(Class.forName('com.alibaba.druid.pool.DruidDataSourceFactory'), 'createDataSource', Map.class).invoke(null, props)
        } catch(ClassNotFoundException ex) {}

        if (!db) {// Hikari 数据源
            try {
                db = (DataSource) Class.forName('com.zaxxer.hikari.HikariDataSource').newInstance()
                dsAttr.each {k, v ->
                    if (datasource.hasProperty(k)) {
                        def t = datasource[(k)].getClass()
                        if (t == Integer || t == int) datasource.(k) = Integer.valueOf(v)
                        else if (t == Long || t == long) datasource.(k) = Long.valueOf(v)
                        else if (t == Boolean || t == boolean) datasource.(k) = Boolean.valueOf(v)
                        else datasource.(k) = v
                    }
                }
            } catch (ClassNotFoundException ex) {}
        }

        // dbcp2 数据源
        if (!db) {
            try {
                Map props = new HashMap()
                dsAttr.each {props.put(it.key, Objects.toString(it.value, ''))}
                if (!props.containsKey('validationQuery')) props.put('validationQuery', 'select 1')
                db = (DataSource) Utils.findMethod(Class.forName('org.apache.commons.dbcp2.BasicDataSourceFactory'), 'createDataSource', Properties.class).invoke(null, props)
            } catch (ClassNotFoundException ex) {}
            return db
        }
    }
}

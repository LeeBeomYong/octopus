/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kr.co.bitnine.octopus.schema;

import com.google.common.collect.ImmutableMap;
import kr.co.bitnine.octopus.schema.model.*;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.tools.Frameworks;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.metamodel.DataContext;
import org.apache.metamodel.DataContextFactory;
import org.apache.metamodel.schema.Schema;

import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Query;
import javax.jdo.Transaction;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Octopus MetaStore
 *
 * Octopus MetaStore has datasource information; schemas, tables and columns.
 * It should support fast search on the datasource information.
 */
public class MetaStore
{
    private static final Log LOG = LogFactory.getLog(MetaStore.class);

    private static PersistenceManagerFactory pmf = null;

    private static final ThreadLocal<MetaStore> tlsMetaStore = new ThreadLocal() {
        @Override
        protected Object initialValue()
        {
            return null;
        }
    };

    /* lock structure to synchronize requests for Calcite schema */
    private static final ReadWriteLock lock = new ReentrantReadWriteLock(false);
    private static final Lock readLock = lock.readLock();
    private static final Lock writeLock = lock.writeLock();

    public static void init(Configuration conf)
    {
        if (pmf == null) {
            /* initialize datanucleus */
            Properties prop = new Properties();
            prop.setProperty("javax.jdo.PersistenceManagerFactoryClass", "org.datanucleus.api.jdo.JDOPersistenceManagerFactory");
            prop.setProperty("datanucleus.ConnectionURL", conf.get("metastore.connection.URL"));
            prop.setProperty("datanucleus.ConnectionDriverName", conf.get("metastore.connection.drivername"));
            prop.setProperty("datanucleus.ConnectionUserName", conf.get("metastore.connection.username"));
            prop.setProperty("datanucleus.ConnectionPassword", conf.get("metastore.connection.password"));
            prop.setProperty("datanucleus.schema.autoCreateAll", "true");

            if (conf.get("metastore.connection.drivername").equals("org.sqlite.JDBC")) {
                /* NOTE: using SQLite with sequences */
                prop.setProperty("datanucleus.valuegeneration.transactionAttribute", "UsePM");
                /* NOTE: using SQLite with connection pooling occurs NullPointerException */
                prop.setProperty("datanucleus.connectionPoolingType", "None");
                prop.setProperty("datanucleus.connectionPoolingType.nontx", "None");
            }

            pmf = JDOHelper.getPersistenceManagerFactory(prop);
        }

        PersistenceManager pm = pmf.getPersistenceManager();
        Query query = pm.newQuery(MUser.class);
        query.setFilter("name == 'octopus'");
        query.setUnique(true);
        MUser user = (MUser) query.execute();
        if (user == null) {
            user = new MUser("octopus", "bitnine");
            pm.makePersistent(user);
        }

        /* read entire MetaStore data and make Calcite Schema instances */
        query = pm.newQuery(MDataSource.class);
        for (MDataSource mdatasource : (List<MDataSource>) query.execute()) {
            add(mdatasource.getName(), mdatasource);
        }
        pm.close();
    }

    public static MetaStore get()
    {
        MetaStore ms = tlsMetaStore.get();
        if (ms == null) {
            ms = new MetaStore();
            tlsMetaStore.set(ms);
            ms = tlsMetaStore.get();
        }

        return ms;
    }

    private PersistenceManager pm;

    private MetaStore()
    {
        pm = pmf.getPersistenceManager();
    }

    /* This method is for destroying a metastore of one thread.
       So pmf is not closed. */
    public void destroy()
    {
        tlsMetaStore.remove();
        pm.close();
    }

    public void createUser(String name, String password)
    {
        MUser user = new MUser(name, password);
        pm.makePersistent(user);
    }

    public MUser getUserByName(String name)
    {
        Query query = pm.newQuery(MUser.class);
        query.setFilter("name == '" + name + "'");
        query.setUnique(true);

        return (MUser) query.execute();
    }

    private static final SchemaPlus rootSchema = Frameworks.createRootSchema(false);

    public static void add(String name, final MDataSource dataSource)
    {
        writeLock.lock();
        rootSchema.add(name,
                new org.apache.calcite.schema.impl.AbstractSchema() {
                    private final ImmutableMap<String, org.apache.calcite.schema.Schema> subSchemaMap;

                    {
                        ImmutableMap.Builder<String, org.apache.calcite.schema.Schema> builder = ImmutableMap.builder();

                        for (MSchema schema : dataSource.getSchemas()) {
                            String name = schema.getName();
                            builder.put(name,  new OctopusSchema(schema));
                        }

                        subSchemaMap = builder.build();
                    }

                    @Override
                    public boolean isMutable()
                    {
                        return false;
                    }

                    @Override
                    protected Map<String, org.apache.calcite.schema.Schema> getSubSchemaMap()
                    {
                        return subSchemaMap;
                    }
                });
        writeLock.unlock();
    }

    /* return Calcite schema object */
    public SchemaPlus getCurrentSchema()
    {
        return rootSchema;
    }

    public MTable getTable(String datasource, String schema, String table)
    {
        return null;
    }

    public MTable getTable(String schema, String table)
    {
        return null;
    }

    public MTable getTable(String tableName)
    {
        Query query = pm.newQuery(MTable.class);
        query.setFilter("name == '" + tableName + "'");

        // if there are more results than just 1, JDOUserException will occur
        query.setUnique(true);

        MTable table = (MTable) query.execute();

        return table;
    }

    public List<MDataSource> getDatasources()
    {
        Query query = pm.newQuery("javax.jdo.query.SQL", "SELECT * FROM \"MDATASOURCE\"");
        query.setClass(MDataSource.class);
        List<MDataSource> results = (List) query.execute();
        return results;
    }

    /*
     * add DataSource using previously-made connection
     * this method is for unit test using in-memory sqlite
     */
    public void addDataSource(String name, String jdbc_driver, String jdbc_connectionString, Connection conn, String description) throws Exception
    {
        // Get schema information using Metamodel

        int type = 0; // TODO: make connection type enum (e.g. JDBC)
        DataContext dc = DataContextFactory.createJdbcDataContext(conn);

        Transaction tx = pm.currentTransaction();
        try {
            tx.begin();

            // create MDataSource
            MDataSource mds = new MDataSource(name, type, jdbc_driver, jdbc_connectionString, description);
            pm.makePersistent(mds);

            // read schema, table, column information and make corresponding model classes
            for (Schema schema : dc.getSchemas()) {
                String schemaName = schema.getName();
                if (schemaName == null)
                    schemaName = "__DEFAULT"; // FIXME

                MSchema mschema = new MSchema(schemaName, mds);
                pm.makePersistent(mschema);

                for (org.apache.metamodel.schema.Table table : schema.getTables()) {
                    String tableName = table.getName();

                    MTable mtable = new MTable(tableName, 0, "", mschema);
                    pm.makePersistent(mtable);

                    for (org.apache.metamodel.schema.Column col : table.getColumns()) {
                        String colName = col.getName();

                        int jdbcType = col.getType().getJdbcType();
                        SqlTypeName typeName = SqlTypeName.getNameForJdbcType(jdbcType);
                        MColumn mcolumn = new MColumn(colName, jdbcType, "", mtable);
                        pm.makePersistent(mcolumn);
                    }
                }
            }
            add(name, mds);

            tx.commit();
        } catch (Exception e) {
            LOG.error(ExceptionUtils.getStackTrace(e));
            e.printStackTrace();
        } finally {
            if (tx.isActive())
                tx.rollback();
        }
    }

    public void addDataSource(String name, String jdbc_driver, String jdbc_connectionString, String description) throws Exception
    {
        Class.forName(jdbc_driver);
        Connection conn = DriverManager.getConnection(jdbc_connectionString);
        addDataSource(name, jdbc_driver, jdbc_connectionString, conn, description);
    }

    public MDataSource getDatasource(String datasource)
    {
        Query query = pm.newQuery("javax.jdo.query.SQL", "SELECT * FROM \"MDATASOURCE\" WHERE \"NAME\" = '" + datasource +"'");
        query.setClass(MDataSource.class);
        List<MDataSource> results = (List<MDataSource>) query.execute();
        /* fixme: not found exception */
        return results.get(0);
    }

    public MTable getTable(SqlIdentifier tableID)
    {
        Query query = pm.newQuery(MTable.class);
        switch (tableID.names.size()) {
            case 1:
                query.setFilter("name == '" + tableID.toString() + "'");
                break;
            case 2:
                query.setFilter("this.schemas.name == '" + tableID.names.get(0) + "' && " +
                                "name == '" + tableID.names.get(1) + "'");
                break;
            case 3:
                query.setFilter("this.schema.datasource.name == '" + tableID.names.get(0) + "' && " +
                                "this.schema.name == '" + tableID.names.get(1) + "' && " +
                                "name == '" + tableID.names.get(2) + "'");
                break;
            default:
                throw new RuntimeException("invalid SqlIdentifier size: " + tableID.names.size());
        }
        List<MTable> results = (List<MTable>) query.execute();

        if (results.size() == 0) {
            /* fixme: not found exception */
        } else if (results.size() > 1) {
            /* fixme: ambiguous table name exception */
        }

        LOG.debug("table found: " + results.get(0).getName());
        return results.get(0);
    }

    public void getReadLock()
    {
        readLock.lock();
    }

    public void releaseReadLock()
    {
        readLock.unlock();
    }

    /* find a datasource having the specified table */
/*
    public MDataSource getDatasource(String schema, String tablename) {
        Query query = pm.newQuery("javax.jdo.query.SQL",
                                  "SELECT count(*) FROM \"MDATASOURCE\" WHERE \"NAME\" = '" + datasource +"'");
        query.setClass(MDataSource.class);
        List<MDataSource> results = (List<MDataSource>) query.execute();
        // fixme: not found exception
        return results.get(0);
    }
 */

    // XXX: test ONLY
    public static void closePMF()
    {
        if (pmf != null) {
            pmf.close();
            pmf = null;
        }
    }
}

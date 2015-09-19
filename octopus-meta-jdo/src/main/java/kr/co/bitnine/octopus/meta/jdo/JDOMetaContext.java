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

package kr.co.bitnine.octopus.meta.jdo;

import kr.co.bitnine.octopus.meta.MetaContext;
import kr.co.bitnine.octopus.meta.MetaException;
import kr.co.bitnine.octopus.meta.jdo.model.*;
import kr.co.bitnine.octopus.meta.model.*;
import kr.co.bitnine.octopus.meta.privilege.ObjectPrivilege;
import kr.co.bitnine.octopus.meta.privilege.SystemPrivilege;
import kr.co.bitnine.octopus.util.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.metamodel.DataContext;
import org.apache.metamodel.DataContextFactory;
import org.apache.metamodel.schema.Column;
import org.apache.metamodel.schema.Schema;
import org.apache.metamodel.schema.Table;
import org.apache.metamodel.schema.TableType;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.jdo.Transaction;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;

public class JDOMetaContext implements MetaContext
{
    private static final Log LOG = LogFactory.getLog(JDOMetaContext.class);

    private final PersistenceManager pm;

    public JDOMetaContext(PersistenceManager persistenceManager)
    {
        pm = persistenceManager;
    }

    private MUser getMUser(String name, boolean nothrow) throws MetaException
    {
        try {
            Query query = pm.newQuery(MUser.class);
            query.setFilter("name == userName");
            query.declareParameters("String userName");
            query.setUnique(true);

            MUser mUser = (MUser) query.execute(name);
            if (mUser == null && !nothrow)
                throw new MetaException("user '" + name + "' does not exist");
            return mUser;
        } catch (RuntimeException e) {
            throw new MetaException("failed to get user '" + name + "'", e);
        }
    }

    @Override
    public boolean userExists(String name) throws MetaException
    {
        return getMUser(name, true) != null;
    }

    @Override
    public MetaUser getUser(String name) throws MetaException
    {
        return getMUser(name, false);
    }

    @Override
    public MetaUser createUser(String name, String password) throws MetaException
    {
        try {
            MUser mUser = new MUser(name, password);
            pm.makePersistent(mUser);
            return mUser;
        } catch (RuntimeException e) {
            throw new MetaException("failed to create user '" + name + "'", e);
        }
    }

    @Override
    public void alterUser(String name, String newPassword) throws MetaException
    {
        MUser mUser = (MUser) getUser(name);
        mUser.setPassword(newPassword);
        try {
            pm.makePersistent(mUser);
        } catch (RuntimeException e) {
            throw new MetaException("failed to alter user '" + name + "'", e);
        }
    }

    @Override
    public void dropUser(String name) throws MetaException
    {
        Transaction tx = pm.currentTransaction();
        try {
            tx.begin();

            deleteSchemaPrivilegesByUser(name);
            pm.deletePersistent(getUser(name));

            tx.commit();
        } catch (RuntimeException e) {
            throw new MetaException("failed to drop user '" + name + "'", e);
        } finally {
            if (tx.isActive())
                tx.rollback();
        }
    }

    @Override
    public void commentOnUser(String comment, String name) throws MetaException
    {
        MUser mUser = (MUser) getUser(name);
        mUser.setComment(comment);
        try {
            pm.makePersistent(mUser);
        } catch (RuntimeException e) {
            throw new MetaException("failed to comment on user '" + name + "'", e);
        }
    }

    @Override
    public Collection<MetaUser> getUsers() throws MetaException
    {
        try {
            Query query = pm.newQuery(MUser.class);
            List<MetaUser> users = (List<MetaUser>) query.execute();
            return users;
        } catch (RuntimeException e) {
            throw new MetaException("failed to get user list", e);
        }
    }

    private MDataSource getMDataSource(String name, boolean nothrow) throws MetaException
    {
        try {
            Query query = pm.newQuery(MDataSource.class);
            query.setFilter("name == dataSourceName");
            query.declareParameters("String dataSourceName");
            query.setUnique(true);

            MDataSource mDataSource = (MDataSource) query.execute(name);
            if (mDataSource == null && !nothrow)
                throw new MetaException("data source '" + name + "' does not exist");
            return mDataSource;
        } catch (RuntimeException e) {
            throw new MetaException("failed to get data source '" + name + "'", e);
        }
    }

    @Override
    public MetaDataSource getDataSource(String name) throws MetaException
    {
        return getMDataSource(name, false);
    }

    @Override
    public MetaDataSource addJdbcDataSource(String driverName, String connectionString, String name) throws MetaException
    {
        if (getMDataSource(name, true) != null)
            throw new MetaException("data source '" + name + "' already exists");

        // TODO: use another ClassLoader to load JDBC drivers
        LOG.debug("addJdbcDataSource. driverName=" + driverName + ", connectionString=" + connectionString + ", name=" + name);
        try {
            Class.forName(driverName);
        } catch (ClassNotFoundException e) {
            throw new MetaException(e);
        }

        Transaction tx = pm.currentTransaction();
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(connectionString);
            DataContext dc = DataContextFactory.createJdbcDataContext(conn);

            tx.begin();

            MDataSource mDataSource = new MDataSource(name, 0, driverName, connectionString);
            pm.makePersistent(mDataSource);

            addJdbcDataSourceInternal(mDataSource, dc);

            tx.commit();

            LOG.debug("complete addJdbcDataSource");
            return mDataSource;
        } catch (Exception e) {
            throw new MetaException("failed to add data source", e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) { }
            }
            if (tx.isActive())
                tx.rollback();
        }
    }

    @Override
    public void dropJdbcDataSource(String name) throws MetaException
    {
        Transaction tx = pm.currentTransaction();

        try {
            tx.begin();

            // remove all object privileges related to this datasource
            deleteSchemaPrivilegesByDataSource(name);
            pm.deletePersistent(getDataSource(name));

            tx.commit();
        } catch (RuntimeException e) {
            throw new MetaException("failed to drop dataSource '" + name + "'", e);
        } finally {
            if (tx.isActive())
                tx.rollback();
        }
    }

    private MColumn addColumn(Column rawColumn, MTable table)
    {
        String columnName = rawColumn.getName();
        int jdbcType = rawColumn.getType().getJdbcType();

        LOG.debug("add column. columnName=" + columnName + ", jdbcType=" + jdbcType);
        return new MColumn(columnName, jdbcType, table);
    }

    private void addColumnsOfTable(Table rawTable, MTable table)
    {
        for (Column rawColumn : rawTable.getColumns())
            pm.makePersistent(addColumn(rawColumn, table));
    }

    private void addTablesOfSchema(Schema rawSchema, MSchema schema)
    {
        for (Table rawTable : rawSchema.getTables()) {
            String tableName = rawTable.getName();
            TableType tableType = rawTable.getType();

            LOG.debug("add table. tableName=" + tableName);
            // TODO: handle table type (SYSTEM_TABLE, ALIAS, SYNONYM etc...)
            MTable table = new MTable(tableName, tableType.name(), schema);
            pm.makePersistent(table);

            addColumnsOfTable(rawTable, table);
        }
    }

    private void addJdbcDataSourceInternal(MDataSource mDataSource, DataContext dc)
    {
        for (Schema rawSchema : dc.getSchemas()) {
            String schemaName = rawSchema.getName();
            if (schemaName == null)
                schemaName = "__DEFAULT";

            LOG.debug("add schema. schemaName=" + schemaName);
            MSchema schema = new MSchema(schemaName, mDataSource);
            pm.makePersistent(schema);

            addTablesOfSchema(rawSchema, schema);
        }
    }

    private void updateColumnsOfTable(Table rawTable, MTable table)
    {
        Map<String, MColumn> oldColumns = new HashMap<>();

        for (MetaColumn col : table.getColumns())
            oldColumns.put(col.getName(), (MColumn) col);

        TreeSet<String> oldColumnNames = new TreeSet<>(oldColumns.keySet());
        TreeSet<String> newColumnNames = new TreeSet<>();

        for (Column rawColumn : rawTable.getColumns()) {
            String colName = rawColumn.getName();

            newColumnNames.add(colName);

            /* NOTE: column order could be wrong! */
            if (!oldColumns.containsKey(colName))
                pm.makePersistent(addColumn(rawColumn, table));
        }

        // remove old columns
        oldColumnNames.removeAll(newColumnNames);
        for (String name : oldColumnNames)
            pm.deletePersistent(oldColumns.get(name));
    }

    private void updateTablesOfSchema(Schema rawSchema, MSchema schema, String tablePattern)
    {
        final String tPattern = tablePattern == null ? null : StringUtils.convertPattern(tablePattern);
        Map<String, MTable> oldTables = new HashMap<>();

        for (MetaTable tbl : schema.getTables())
            oldTables.put(tbl.getName(), (MTable) tbl);

        TreeSet<String> oldTableNames = new TreeSet<>(oldTables.keySet());
        TreeSet<String> newTableNames = new TreeSet<>();

        for (Table rawTable : rawSchema.getTables()) {
            String tableName = rawTable.getName();

            if (tPattern != null && !tableName.matches(tPattern))
                continue;

            LOG.debug("update table. tableName=" + tableName);
            newTableNames.add(tableName);
            if (oldTables.containsKey(tableName)) {
                // update table
                MTable tbl = oldTables.get(tableName);
                updateColumnsOfTable(rawTable, tbl);
            } else {
                // add new table
                MTable tbl = new MTable(tableName, "TABLE", schema);
                pm.makePersistent(tbl);
                addColumnsOfTable(rawTable, tbl);
            }
        }

        // remove old tables
        if (tPattern == null) {
            oldTableNames.removeAll(newTableNames);
            for (String name : oldTableNames)
                pm.deletePersistent(oldTables.get(name));
        }
    }

    private void updateJdbcDataSourceInternal(DataContext dc, MDataSource mDataSource,
                                              String schemaPattern, String tablePattern) throws MetaException
    {
        final String sPattern = schemaPattern == null ? null : StringUtils.convertPattern(schemaPattern);
        Map<String, MSchema> oldSchemas = new HashMap<>();

        for (MetaSchema schem : mDataSource.getSchemas())
            oldSchemas.put(schem.getName(), (MSchema) schem);

        TreeSet<String> oldSchemaNames = new TreeSet<>(oldSchemas.keySet());
        TreeSet<String> newSchemaNames = new TreeSet<>();

        for (Schema rawSchema : dc.getSchemas()) {
            String schemaName = rawSchema.getName();
            if (schemaName == null)
                schemaName = "__DEFAULT";

            if (sPattern != null && !schemaName.matches(sPattern))
                continue;

            LOG.debug("updateSchema. schemaName=" + schemaName);
            newSchemaNames.add(schemaName);
            if (oldSchemas.containsKey(schemaName)) {
                // update schema
                MSchema schema = oldSchemas.get(schemaName);
                updateTablesOfSchema(rawSchema, schema, tablePattern);
            } else {
                // add new schema
                MSchema schema = new MSchema(schemaName, mDataSource);
                pm.makePersistent(schema);
                addTablesOfSchema(rawSchema, schema);
            }
        }

        // remove old schemas
        if (sPattern == null) {
            oldSchemaNames.removeAll(newSchemaNames);
            for (String name : oldSchemaNames) {
                deleteSchemaPrivilegeBySchema(mDataSource.getName(), name);
                pm.deletePersistent(oldSchemas.get(name));
            }
        }
    }

    @Override
    public MetaDataSource updateJdbcDataSource(String dataSource, String schema, String table) throws MetaException
    {
        MetaDataSource dataSrc = getMDataSource(dataSource, false);
        String connectionString = dataSrc.getConnectionString();

        Transaction tx = pm.currentTransaction();
        try {
            Connection conn = DriverManager.getConnection(connectionString);
            DataContext dc = DataContextFactory.createJdbcDataContext(conn);

            tx.begin();

            updateJdbcDataSourceInternal(dc, (MDataSource)dataSrc, schema, table);

            tx.commit();
        } catch (Exception e) {
            throw new MetaException("failed to update dataSource '" + dataSource + "'", e);
        } finally {
            if (tx.isActive())
                tx.rollback();
        }

        return dataSrc;
    }

    @Override
    public void commentOnDataSource(String comment, String name) throws MetaException
    {
        MDataSource mDataSource = (MDataSource) getDataSource(name);
        mDataSource.setComment(comment);
        try {
            pm.makePersistent(mDataSource);
        } catch (RuntimeException e) {
            throw new MetaException("failed to comment on data source '" + name + "'", e);
        }
    }

    @Override
    public Collection<MetaDataSource> getDataSources() throws MetaException
    {
        try {
            Query query = pm.newQuery(MDataSource.class);
            List<MetaDataSource> dataSources = (List<MetaDataSource>) query.execute();
            return dataSources;
        } catch (RuntimeException e) {
            throw new MetaException("failed to get data sources", e);
        }
    }

    MetaSchema getSchemaByQualifiedName(String dataSourceName, String schemaName) throws MetaException
    {
        try {
            Query query = pm.newQuery(MSchema.class);
            query.setFilter("name == schemaName && " +
                    "dataSource.name == dataSourceName");
            query.declareParameters("String dataSourceName, String schemaName");
            query.setUnique(true);

            MSchema mSchema = (MSchema) query.execute(dataSourceName, schemaName);
            if (mSchema == null)
                throw new MetaException("schema '" + dataSourceName + "." + schemaName + "' does not exist");
            return mSchema;
        } catch (RuntimeException e) {
            throw new MetaException("failed to get schema '" + dataSourceName + "." + schemaName + "'", e);
        }
    }

    @Override
    public void commentOnSchema(String comment, String dataSourceName, String schemaName) throws MetaException
    {
        MSchema mSchema = (MSchema) getSchemaByQualifiedName(dataSourceName, schemaName);
        mSchema.setComment(comment);
        try {
            pm.makePersistent(mSchema);
        } catch (RuntimeException e) {
            throw new MetaException("failed to comment on schema '" + dataSourceName + "." + schemaName + "'", e);
        }
    }

    MetaTable getTableByQualifiedName(String dataSourceName, String schemaName, String tableName) throws MetaException
    {
        try {
            Query query = pm.newQuery(MTable.class);
            query.setFilter("name == tableName && " +
                    "schema.name == schemaName && " +
                    "schema.dataSource.name == dataSourceName");
            query.declareParameters("String dataSourceName, String schemaName, String tableName");
            query.setUnique(true);

            MTable mTable = (MTable) query.execute(dataSourceName, schemaName, tableName);
            if (mTable == null)
                throw new MetaException("table '" + dataSourceName + "." + schemaName + "." + tableName +"' does not exist");
            return mTable;
        } catch (RuntimeException e) {
            throw new MetaException("failed to get table '" + dataSourceName + "." + schemaName + "." + tableName + "'", e);
        }
    }

    @Override
    public void commentOnTable(String comment, String dataSourceName, String schemaName, String tableName) throws MetaException
    {
        MTable mTable = (MTable) getTableByQualifiedName(dataSourceName, schemaName, tableName);
        mTable.setComment(comment);
        try {
            pm.makePersistent(mTable);
        } catch (RuntimeException e) {
            throw new MetaException("failed to comment on table '" + dataSourceName + "." + schemaName + "." + tableName + "'", e);
        }
    }

    MetaColumn getColumnByQualifiedName(String dataSourceName, String schemaName, String tableName, String columnName) throws MetaException
    {
        try {
            Query query = pm.newQuery(MColumn.class);
            query.setFilter("name == columnName && " +
                    "table.name == tableName && " +
                    "table.schema.name == schemaName && " +
                    "table.schema.dataSource.name == dataSourceName");
            query.declareParameters("String dataSourceName, String schemaName, String tableName, String columnName");
            query.setUnique(true);

            MColumn mColumn = (MColumn) query.executeWithArray(dataSourceName, schemaName, tableName, columnName);
            if (mColumn == null)
                throw new MetaException("column '" + dataSourceName + "." + schemaName + "." + tableName + "." + columnName + "' does not exist");
            return mColumn;
        } catch (RuntimeException e) {
            throw new MetaException("failed to get column '" + dataSourceName + "." + schemaName + "." + tableName + "." + columnName + "'", e);
        }
    }

    @Override
    public void commentOnColumn(String comment, String dataSourceName, String schemaName, String tableName, String columnName) throws MetaException
    {
        MColumn mColumn = (MColumn) getColumnByQualifiedName(dataSourceName, schemaName, tableName, columnName);
        mColumn.setComment(comment);
        try {
            pm.makePersistent(mColumn);
        } catch (RuntimeException e) {
            throw new MetaException("failed to comment on column '" + dataSourceName + "." + schemaName + "." + tableName + "." + columnName + "'", e);
        }
    }

    @Override
    public void setDataCategoryOn(String category, String dataSourceName, String schemaName, String tableName, String columnName) throws MetaException
    {
        MColumn mColumn = (MColumn) getColumnByQualifiedName(dataSourceName, schemaName, tableName, columnName);
        mColumn.setDataCategory(category);
        try {
            pm.makePersistent(mColumn);
        } catch (RuntimeException e) {
            throw new MetaException("failed to set data category on column '" + dataSourceName + "." + schemaName + "." + tableName + "." + columnName + "'", e);
        }
    }

    @Override
    public MetaRole createRole(String name) throws MetaException
    {
        try {
            MRole mRole = new MRole(name);
            pm.makePersistent(mRole);
            return mRole;
        } catch (RuntimeException e) {
            throw new MetaException("failed to create role '" + name + "'");
        }
    }

    @Override
    public void dropRoleByName(String name) throws MetaException
    {
        try {
            Query query = pm.newQuery(MRole.class);
            query.setFilter("name == '" + name + "'");
            query.setUnique(true);

            MRole mRole = (MRole) query.execute();
            if (mRole == null)
                throw new MetaException("role '" + name + "' does not exist");

            pm.deletePersistent(mRole);
        } catch (RuntimeException e) {
            throw new MetaException("failed to drop role '" + name + "'", e);
        }
    }

    @Override
    public void addSystemPrivileges(List<SystemPrivilege> sysPrivs, List<String> userNames) throws MetaException
    {
        Transaction tx = pm.currentTransaction();
        try {
            tx.begin();

            for (String userName : userNames) {
                MUser mUser = (MUser) getUser(userName);
                for (SystemPrivilege sysPriv : sysPrivs)
                    mUser.addSystemPrivilege(sysPriv);

                pm.makePersistent(mUser);
            }

            tx.commit();
        } catch (Exception e) {
            throw new MetaException("failed to add system privileges to users", e);
        } finally {
            if (tx.isActive())
                tx.rollback();
        }
    }

    @Override
    public void removeSystemPrivileges(List<SystemPrivilege> sysPrivs, List<String> userNames) throws MetaException
    {
        Transaction tx = pm.currentTransaction();
        try {
            tx.begin();

            for (String userName : userNames) {
                MUser mUser = (MUser) getUser(userName);
                for (SystemPrivilege sysPriv : sysPrivs)
                    mUser.removeSystemPrivilege(sysPriv);

                pm.makePersistent(mUser);
            }

            tx.commit();
        } catch (Exception e) {
            throw new MetaException("failed to remove system privileges from users", e);
        } finally {
            if (tx.isActive())
                tx.rollback();
        }
    }

    @Override
    public MetaSchemaPrivilege getSchemaPrivilege(String[] schemaName, String userName) throws MetaException
    {
        assert schemaName.length == 2;

        try {
            Query query = pm.newQuery(MSchemaPrivilege.class);
            query.setFilter("schema.name == schemaName && " +
                    "schema.dataSource.name == dataSourceName && " +
                    "user.name == userName");
            query.declareParameters("String dataSourceName, String schemaName, String userName");
            query.setUnique(true);

            return (MSchemaPrivilege) query.execute(schemaName[0], schemaName[1], userName);
        } catch (RuntimeException e) {
            throw new MetaException("failed to get schema privilege of schemaName=" + schemaName[0] + "." + schemaName[1] + ", userName=" + userName, e);
        }
    }

    @Override
    public Collection<MetaSchemaPrivilege> getSchemaPrivilegesByUser(String userName) throws MetaException
    {
        try {
            Query query = pm.newQuery(MSchemaPrivilege.class);
            query.setFilter("user.name == userName");
            query.declareParameters("String userName");

            return (List<MetaSchemaPrivilege>) query.execute(userName);
        } catch (RuntimeException e) {
            throw new MetaException("failed to get schema privileges on user '" + userName + "'", e);
        }
    }

    @Override
    public void addObjectPrivileges(List<ObjectPrivilege> objPrivs, String[] schemaName, List<String> userNames) throws MetaException
    {
        assert schemaName.length == 2;

        Transaction tx = pm.currentTransaction();
        try {
            tx.begin();

            for (String userName : userNames) {
                MSchemaPrivilege mSchemaPriv = (MSchemaPrivilege) getSchemaPrivilege(schemaName, userName);
                if (mSchemaPriv == null) {
                    MSchema mSchema = (MSchema) getSchemaByQualifiedName(schemaName[0], schemaName[1]);
                    MUser mUser = (MUser) getUser(userName);
                    mSchemaPriv = new MSchemaPrivilege(mSchema, mUser);
                }

                for (ObjectPrivilege objPriv : objPrivs)
                    mSchemaPriv.addObjectPrivilege(objPriv);

                pm.makePersistent(mSchemaPriv);
            }

            tx.commit();
        } catch (Exception e) {
            throw new MetaException("failed to add object privileges on " + schemaName[0] + "." + schemaName[1] + "to users", e);
        } finally {
            if (tx.isActive())
                tx.rollback();
        }
    }

    @Override
    public void removeObjectPrivileges(List<ObjectPrivilege> objPrivs, String[] schemaName, List<String> userNames) throws MetaException
    {
        assert schemaName.length == 2;

        Transaction tx = pm.currentTransaction();
        try {
            tx.begin();

            for (String userName : userNames) {
                MSchemaPrivilege mSchemaPriv = (MSchemaPrivilege) getSchemaPrivilege(schemaName, userName);
                if (mSchemaPriv == null)
                    continue;

                for (ObjectPrivilege objPriv : objPrivs)
                    mSchemaPriv.removeObjectPrivilege(objPriv);

                if (mSchemaPriv.isEmpty())
                    pm.deletePersistent(mSchemaPriv);
                else
                    pm.makePersistent(mSchemaPriv);
            }

            tx.commit();
        } catch (Exception e) {
            throw new MetaException("failed to remove object privileges on " + schemaName[0] + "." + schemaName[1] + "to users", e);
        } finally {
            if (tx.isActive())
                tx.rollback();
        }
    }

    private void deleteSchemaPrivilegesByUser(String userName) throws MetaException
    {
        try {
            Query query = pm.newQuery(MSchemaPrivilege.class);
            query.setFilter("user.name == userName");
            query.declareParameters("String userName");
            query.deletePersistentAll(userName);
        } catch (RuntimeException e) {
            throw new MetaException("failed to delete schema privileges of userName=" + userName, e);
        }
    }

    private void deleteSchemaPrivilegesByDataSource(String dataSourceName) throws MetaException
    {
        try {
            Query query = pm.newQuery(MSchemaPrivilege.class);
            query.setFilter("schema.dataSource.name == dataSourceName");
            query.declareParameters("String dataSourceName");
            query.deletePersistentAll(dataSourceName);
        } catch (RuntimeException e) {
            throw new MetaException("failed to delete schema privileges of dataSourceName=" + dataSourceName, e);
        }
    }

    private void deleteSchemaPrivilegeBySchema(String dataSourceName, String schemaName) throws MetaException
    {
        try {
            Query query = pm.newQuery(MSchemaPrivilege.class);
            query.setFilter("schema.dataSource.name == dataSourceName && " +
                    "schema.name == schemaName");
            query.declareParameters("String dataSourceName, String schemaName");
            query.deletePersistentAll(dataSourceName, schemaName);
        } catch (RuntimeException e) {
            throw new MetaException("failed to delete schema privileges", e);
        }
    }

    @Override
    public void close()
    {
        pm.close();
    }
}

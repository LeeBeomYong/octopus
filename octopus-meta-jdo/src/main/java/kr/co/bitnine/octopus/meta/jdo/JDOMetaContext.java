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

import java.util.ArrayList;
import kr.co.bitnine.octopus.meta.MetaContext;
import kr.co.bitnine.octopus.meta.MetaException;
import kr.co.bitnine.octopus.meta.logs.UpdateLogger;
import kr.co.bitnine.octopus.meta.logs.UpdateLoggerFactory;
import kr.co.bitnine.octopus.meta.result.ResultOfGetColumns;
import kr.co.bitnine.octopus.meta.jdo.model.MColumn;
import kr.co.bitnine.octopus.meta.jdo.model.MDataSource;
import kr.co.bitnine.octopus.meta.jdo.model.MRole;
import kr.co.bitnine.octopus.meta.jdo.model.MSchema;
import kr.co.bitnine.octopus.meta.jdo.model.MSchemaPrivilege;
import kr.co.bitnine.octopus.meta.jdo.model.MTable;
import kr.co.bitnine.octopus.meta.jdo.model.MUser;
import kr.co.bitnine.octopus.meta.model.MetaColumn;
import kr.co.bitnine.octopus.meta.model.MetaDataSource;
import kr.co.bitnine.octopus.meta.model.MetaRole;
import kr.co.bitnine.octopus.meta.model.MetaSchema;
import kr.co.bitnine.octopus.meta.model.MetaSchemaPrivilege;
import kr.co.bitnine.octopus.meta.model.MetaTable;
import kr.co.bitnine.octopus.meta.model.MetaUser;
import kr.co.bitnine.octopus.meta.privilege.ObjectPrivilege;
import kr.co.bitnine.octopus.meta.privilege.SystemPrivilege;
import org.apache.commons.lang3.StringUtils;
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
import java.sql.Types;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class JDOMetaContext implements MetaContext {
    private static final Log LOG = LogFactory.getLog(JDOMetaContext.class);

    private final PersistenceManager pm;
    private final UpdateLoggerFactory updateLoggerFactory;

    private UpdateLogger updateLogger;

    public JDOMetaContext(PersistenceManager persistenceManager,
                          UpdateLoggerFactory updateLoggerFactory) {
        pm = persistenceManager;
        this.updateLoggerFactory = updateLoggerFactory;
    }

    private MUser getMUser(String name, boolean nothrow) throws MetaException {
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
    public boolean userExists(String name) throws MetaException {
        return getMUser(name, true) != null;
    }

    @Override
    public MetaUser getUser(String name) throws MetaException {
        return getMUser(name, false);
    }

    @Override
    public MetaUser createUser(String name, String password) throws MetaException {
        try {
            MUser mUser = new MUser(name, password);
            pm.makePersistent(mUser);
            return mUser;
        } catch (RuntimeException e) {
            throw new MetaException("failed to create user '" + name + "'", e);
        }
    }

    @Override
    public void alterUser(String name, String newPassword) throws MetaException {
        Transaction tx = pm.currentTransaction();
        try {
            tx.begin();

            MUser mUser = (MUser) getUser(name);
            mUser.setPassword(newPassword);
            pm.makePersistent(mUser);

            tx.commit();
        } catch (RuntimeException e) {
            throw new MetaException("failed to alter user '" + name + "'", e);
        } finally {
            if (tx.isActive())
                tx.rollback();
        }
    }

    @Override
    public void dropUser(String name) throws MetaException {
        Transaction tx = pm.currentTransaction();
        try {
            tx.begin();

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
    public void commentOnUser(String comment, String name) throws MetaException {
        Transaction tx = pm.currentTransaction();
        try {
            tx.begin();

            MUser mUser = (MUser) getUser(name);
            mUser.setComment(comment);
            pm.makePersistent(mUser);

            tx.commit();
        } catch (RuntimeException e) {
            throw new MetaException("failed to comment on user '" + name + "'", e);
        } finally {
            if (tx.isActive())
                tx.rollback();
        }
    }

    @Override
    public Collection<MetaUser> getUsers() throws MetaException {
        try {
            Query query = pm.newQuery(MUser.class);
            List<MetaUser> users = (List<MetaUser>) query.execute();
            return users;
        } catch (RuntimeException e) {
            throw new MetaException("failed to get user list", e);
        }
    }

    private MDataSource getMDataSource(String name, boolean nothrow) throws MetaException {
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
    public boolean dataSourceExists(String name) throws MetaException {
        return getMDataSource(name, true) != null;
    }

    @Override
    public MetaDataSource getDataSource(String name) throws MetaException {
        return getMDataSource(name, false);
    }

    @Override
    public MetaDataSource addJdbcDataSource(String driverName, String connectionString, String name) throws MetaException {
        if (dataSourceExists(name))
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

            addJdbcDataSourceInternal(dc, mDataSource);

            tx.commit();

            LOG.debug("complete addJdbcDataSource");
            return mDataSource;
        } catch (MetaException me) {
            throw me;
        } catch (Exception e) {
            throw new MetaException("failed to add data source '" + name + "' - " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException ignore) { }
            }
            if (tx.isActive())
                tx.rollback();
        }
    }

    @Override
    public void dropJdbcDataSource(String name) throws MetaException {
        Transaction tx = pm.currentTransaction();
        try {
            tx.begin();

            pm.deletePersistent(getDataSource(name));

            tx.commit();
        } catch (RuntimeException e) {
            throw new MetaException("failed to drop dataSource '" + name + "' - " + e.getMessage(), e);
        } finally {
            if (tx.isActive())
                tx.rollback();
        }
    }

    private void addColumn(Column rawColumn, MTable mTable) {
        String columnName = rawColumn.getName();
        int jdbcType = rawColumn.getType().getJdbcType();
        int typeInfo = -1;
        if (jdbcType == Types.VARCHAR)
            typeInfo = rawColumn.getColumnSize();

        LOG.debug("add column. columnName=" + columnName + ", jdbcType=" + jdbcType + ", typeInfo=" + typeInfo);
        MColumn mColumn = new MColumn(columnName, jdbcType, typeInfo, mTable);
        pm.makePersistent(mColumn);
    }

    private Column[] getRawTableColumns(Table rawTable) throws MetaException {
        Column[] rawColumns = rawTable.getColumns();
        if (rawColumns == null || rawColumns.length < 1)
            throw new MetaException("table '" + rawTable.getName() + "' has no column");

        return rawColumns;
    }

    private void addColumnsOfTable(Table rawTable, MTable mTable) throws MetaException {
        for (Column rawColumn : getRawTableColumns(rawTable))
            addColumn(rawColumn, mTable);
    }

    private void addTablesOfSchema(Schema rawSchema, MSchema mSchema, UpdateLogger upLog) throws MetaException {
        for (Table rawTable : rawSchema.getTables()) {
            String tableName = rawTable.getName();
            TableType tableType = rawTable.getType();

            LOG.debug("add table. tableName=" + tableName);
            // TODO: handle table type (SYSTEM_TABLE, ALIAS, SYNONYM etc...)
            MTable mTable = new MTable(tableName, tableType.name(), mSchema);
            pm.makePersistent(mTable);
            if (upLog != null)
                upLog.create(null, tableName);

            addColumnsOfTable(rawTable, mTable);
        }
    }

    private void addJdbcDataSourceInternal(DataContext dc, MDataSource mDataSource) throws MetaException {
        for (Schema rawSchema : dc.getSchemas()) {
            String schemaName = rawSchema.getName();
            if (schemaName == null)
                schemaName = "__DEFAULT";

            LOG.debug("add schema. schemaName=" + schemaName);
            MSchema mSchema = new MSchema(schemaName, mDataSource);
            pm.makePersistent(mSchema);

            addTablesOfSchema(rawSchema, mSchema, null);
        }
    }

    private void updateColumnsOfTable(Table rawTable, MTable mTable) throws MetaException {
        Map<String, MColumn> oldColumns = new HashMap<>();

        for (MetaColumn col : mTable.getColumns())
            oldColumns.put(col.getName(), (MColumn) col);

        Set<String> oldColumnNames = new TreeSet<>(oldColumns.keySet());
        Set<String> newColumnNames = new TreeSet<>();

        for (Column rawColumn : getRawTableColumns(rawTable)) {
            String colName = rawColumn.getName();

            newColumnNames.add(colName);

            /* NOTE: column order could be wrong! */
            if (!oldColumns.containsKey(colName))
                addColumn(rawColumn, mTable);
        }

        // remove old columns
        oldColumnNames.removeAll(newColumnNames);
        for (String name : oldColumnNames)
            pm.deletePersistent(oldColumns.get(name));
    }

    private void updateTablesOfSchema(Schema rawSchema, MSchema mSchema, final String tableRegex) throws MetaException {
        Map<String, MTable> oldTables = new HashMap<>();

        for (MetaTable table : mSchema.getTables())
            oldTables.put(table.getName(), (MTable) table);

        Set<String> oldTableNames = new TreeSet<>(oldTables.keySet());
        Set<String> newTableNames = new TreeSet<>();

        for (Table rawTable : rawSchema.getTables()) {
            String tableName = rawTable.getName();

            if (tableRegex != null && !tableName.matches(tableRegex))
                continue;

            LOG.debug("update table. tableName=" + tableName);
            newTableNames.add(tableName);
            if (oldTables.containsKey(tableName)) {
                // update table
                MTable mTable = oldTables.get(tableName);
                updateColumnsOfTable(rawTable, mTable);
            } else {
                // add new table
                MTable mTable = new MTable(tableName, "TABLE", mSchema);
                pm.makePersistent(mTable);
                updateLogger.create(null, tableName);
                addColumnsOfTable(rawTable, mTable);
            }
        }

        // remove old tables
        if (tableRegex == null) {
            oldTableNames.removeAll(newTableNames);
            for (String name : oldTableNames) {
                LOG.debug("delete table. tableName=" + mSchema.getName() + '.' + name);
                pm.deletePersistent(oldTables.get(name));
                updateLogger.delete(null, name);
            }
        }
    }

    private void updateJdbcDataSourceInternal(DataContext dc, MDataSource mDataSource,
                                              final String schemaRegex, final String tableRegex) throws MetaException {
        Map<String, MSchema> oldSchemas = new HashMap<>();

        for (MetaSchema schema : mDataSource.getSchemas())
            oldSchemas.put(schema.getName(), (MSchema) schema);

        Set<String> oldSchemaNames = new TreeSet<>(oldSchemas.keySet());
        Set<String> newSchemaNames = new TreeSet<>();

        for (Schema rawSchema : dc.getSchemas()) {
            String schemaName = rawSchema.getName();
            if (schemaName == null)
                schemaName = "__DEFAULT";

            if (schemaRegex != null && !schemaName.matches(schemaRegex))
                continue;

            LOG.debug("update schema. schemaName=" + schemaName);
            newSchemaNames.add(schemaName);
            updateLogger.setDefaultSchema(schemaName);
            if (oldSchemas.containsKey(schemaName)) {
                // update schema
                MSchema mSchema = oldSchemas.get(schemaName);
                updateTablesOfSchema(rawSchema, mSchema, tableRegex);
            } else {
                // add new schema
                MSchema mSchema = new MSchema(schemaName, mDataSource);
                pm.makePersistent(mSchema);
                updateLogger.create(schemaName);
                addTablesOfSchema(rawSchema, mSchema, updateLogger);
            }
        }

        // remove old schemas
        if (schemaRegex == null) {
            oldSchemaNames.removeAll(newSchemaNames);
            for (String name : oldSchemaNames) {
                LOG.debug("delete schema. schemaName=" + name);

                MSchema mSchema = oldSchemas.get(name);

                for (MetaTable table : mSchema.getTables())
                    updateLogger.delete(name, table.getName());

                pm.deletePersistent(oldSchemas.get(name));

                updateLogger.delete(name);
            }
        }
    }

    @Override
    public MetaDataSource updateJdbcDataSource(String dataSourceName, final String schemaRegex, final String tableRegex) throws MetaException {
        MDataSource mDataSource = getMDataSource(dataSourceName, false);

        Transaction tx = pm.currentTransaction();
        Connection conn = null;
        try {
            updateLogger = updateLoggerFactory.createUpdateLogger(dataSourceName);

            String connectionString = mDataSource.getConnectionString();
            conn = DriverManager.getConnection(connectionString);
            DataContext dc = DataContextFactory.createJdbcDataContext(conn);

            updateLogger.begin();
            tx.begin();

            LOG.debug("update data source. dataSourceName=" + dataSourceName);
            updateJdbcDataSourceInternal(dc, mDataSource, schemaRegex, tableRegex);

            tx.commit();
            updateLogger.end();
        } catch (MetaException me) {
            throw me;
        } catch (Exception e) {
            throw new MetaException("failed to update data source '" + dataSourceName + "' - " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException ignore) { }
            }
            if (tx.isActive())
                tx.rollback();

            if (updateLogger != null) {
                updateLogger.close();
                updateLogger = null;
            }
        }

        return mDataSource;
    }

    @Override
    public void commentOnDataSource(String comment, String name) throws MetaException {
        MDataSource mDataSource = (MDataSource) getDataSource(name);
        mDataSource.setComment(comment);
        try {
            pm.makePersistent(mDataSource);
        } catch (RuntimeException e) {
            throw new MetaException("failed to comment on data source '" + name + "'", e);
        }
    }

    @Override
    public Collection<MetaDataSource> getDataSources() throws MetaException {
        try {
            Query query = pm.newQuery(MDataSource.class);
            return (List<MetaDataSource>) query.execute();
        } catch (RuntimeException e) {
            throw new MetaException("failed to get data sources", e);
        }
    }

    @Override
    public Collection<ResultOfGetColumns> getColumns(String dataSourceName, String schemaRegex, String tableRegex, String colmnRegex) throws MetaException {
        try {
            Query query = pm.newQuery(MColumn.class);
            query.setResultClass(ResultOfGetColumns.class);
            query.setResult("this.table.schema.dataSource.name dataSourceName, "
                    + "this.table.schema.name schemaName, "
                    + "this.table.name tableName, "
                    + "this.name columnName, "
                    + "this.type columnType, "
                    + "this.comment comment, "
                    + "this.dataCategory dataCategory, "
                    + "this.table.schema.dataSource.comment dataSourceComment, "
                    + "this.table.schema.comment schemaComment, "
                    + "this.table.comment tableComment");
            ArrayList<String> filters = new ArrayList<>();
            ArrayList<String> parameters = new ArrayList<>();
            Map<String, String> paramValues = new HashMap<>();
            if (dataSourceName != null) {
                filters.add("this.table.schema.dataSource.name == dataSourceName");
                parameters.add("String dataSourceName");
                paramValues.put("dataSourceName", dataSourceName);
            }
            if (schemaRegex != null) {
                filters.add("this.table.schema.name.matches(schemaRegex)");
                parameters.add("String schemaRegex");
                paramValues.put("schemaRegex", schemaRegex);
            }
            if (tableRegex != null) {
                filters.add("this.table.name.matches(tableRegex)");
                parameters.add("String tableRegex");
                paramValues.put("tableRegex", tableRegex);
            }
            if (colmnRegex != null) {
                filters.add("this.name.matches(columnRegex)");
                parameters.add("String columnRegex");
                paramValues.put("columnRegex", colmnRegex);
            }
            if (!filters.isEmpty()) {
                String filter = StringUtils.join(filters, " && ");
                String parameter = StringUtils.join(parameters, ", ");
                query.setFilter(filter);
                query.declareParameters(parameter);
            }
            query.setOrdering("this.table.schema.dataSource.name ASC, "
                    + "this.table.schema.name ASC, "
                    + "this.table.name ASC");
            return  (Collection<ResultOfGetColumns>) query.executeWithMap(paramValues);
        } catch (RuntimeException e) {
            throw new MetaException("failed to get columns", e);
        }
    }

    MetaSchema getSchemaByQualifiedName(String dataSourceName, String schemaName) throws MetaException {
        try {
            Query query = pm.newQuery(MSchema.class);
            query.setFilter("name == schemaName && "
                    + "dataSource.name == dataSourceName");
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
    public void commentOnSchema(String comment, String dataSourceName, String schemaName) throws MetaException {
        MSchema mSchema = (MSchema) getSchemaByQualifiedName(dataSourceName, schemaName);
        mSchema.setComment(comment);
        try {
            pm.makePersistent(mSchema);
        } catch (RuntimeException e) {
            throw new MetaException("failed to comment on schema '" + dataSourceName + "." + schemaName + "'", e);
        }
    }

    MetaTable getTableByQualifiedName(String dataSourceName, String schemaName, String tableName) throws MetaException {
        try {
            Query query = pm.newQuery(MTable.class);
            query.setFilter("name == tableName && "
                    + "schema.name == schemaName && "
                    + "schema.dataSource.name == dataSourceName");
            query.declareParameters("String dataSourceName, String schemaName, String tableName");
            query.setUnique(true);

            MTable mTable = (MTable) query.execute(dataSourceName, schemaName, tableName);
            if (mTable == null)
                throw new MetaException("table '" + dataSourceName + "." + schemaName + "." + tableName + "' does not exist");
            return mTable;
        } catch (RuntimeException e) {
            throw new MetaException("failed to get table '" + dataSourceName + "." + schemaName + "." + tableName + "'", e);
        }
    }

    @Override
    public void commentOnTable(String comment, String dataSourceName, String schemaName, String tableName) throws MetaException {
        MTable mTable = (MTable) getTableByQualifiedName(dataSourceName, schemaName, tableName);
        mTable.setComment(comment);
        try {
            pm.makePersistent(mTable);
        } catch (RuntimeException e) {
            throw new MetaException("failed to comment on table '" + dataSourceName + "." + schemaName + "." + tableName + "'", e);
        }
    }

    MetaColumn getColumnByQualifiedName(String dataSourceName, String schemaName, String tableName, String columnName) throws MetaException {
        try {
            Query query = pm.newQuery(MColumn.class);
            query.setFilter("name == columnName && "
                    + "table.name == tableName && "
                    + "table.schema.name == schemaName && "
                    + "table.schema.dataSource.name == dataSourceName");
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
    public void commentOnColumn(String comment, String dataSourceName, String schemaName, String tableName, String columnName) throws MetaException {
        MColumn mColumn = (MColumn) getColumnByQualifiedName(dataSourceName, schemaName, tableName, columnName);
        mColumn.setComment(comment);
        try {
            pm.makePersistent(mColumn);
        } catch (RuntimeException e) {
            throw new MetaException("failed to comment on column '" + dataSourceName + "." + schemaName + "." + tableName + "." + columnName + "'", e);
        }
    }

    @Override
    public void setDataCategoryOn(String category, String dataSourceName, String schemaName, String tableName, String columnName) throws MetaException {
        MColumn mColumn = (MColumn) getColumnByQualifiedName(dataSourceName, schemaName, tableName, columnName);
        mColumn.setDataCategory(category);
        try {
            pm.makePersistent(mColumn);
        } catch (RuntimeException e) {
            throw new MetaException("failed to set data category on column '" + dataSourceName + "." + schemaName + "." + tableName + "." + columnName + "'", e);
        }
    }

    @Override
    public MetaRole createRole(String name) throws MetaException {
        try {
            MRole mRole = new MRole(name);
            pm.makePersistent(mRole);
            return mRole;
        } catch (RuntimeException e) {
            throw new MetaException("failed to create role '" + name + "'");
        }
    }

    @Override
    public void dropRoleByName(String name) throws MetaException {
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
    public void addSystemPrivileges(List<SystemPrivilege> sysPrivs, List<String> userNames) throws MetaException {
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
    public void removeSystemPrivileges(List<SystemPrivilege> sysPrivs, List<String> userNames) throws MetaException {
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
    public MetaSchemaPrivilege getSchemaPrivilege(String[] schemaName, String userName) throws MetaException {
        assert schemaName.length == 2;

        try {
            Query query = pm.newQuery(MSchemaPrivilege.class);
            query.setFilter("schema.name == schemaName && "
                    + "schema.dataSource.name == dataSourceName && "
                    + "user.name == userName");
            query.declareParameters("String dataSourceName, String schemaName, String userName");
            query.setUnique(true);

            return (MSchemaPrivilege) query.execute(schemaName[0], schemaName[1], userName);
        } catch (RuntimeException e) {
            throw new MetaException("failed to get schema privilege of schemaName=" + schemaName[0] + "." + schemaName[1] + ", userName=" + userName, e);
        }
    }

    @Override
    public Collection<MetaSchemaPrivilege> getSchemaPrivilegesByUser(String userName) throws MetaException {
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
    public void addObjectPrivileges(List<ObjectPrivilege> objPrivs, String[] schemaName, List<String> userNames) throws MetaException {
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
    public void removeObjectPrivileges(List<ObjectPrivilege> objPrivs, String[] schemaName, List<String> userNames) throws MetaException {
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

    @Override
    public void close() {
        pm.close();
    }
}

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

package kr.co.bitnine.octopus.sql;

import kr.co.bitnine.octopus.meta.privilege.ObjectPrivilege;
import kr.co.bitnine.octopus.meta.privilege.SystemPrivilege;
import mockit.Mocked;
import mockit.Verifications;
import mockit.VerificationsInOrder;
import mockit.integration.junit4.JMockit;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.*;

@RunWith(JMockit.class)
public class OctopusSqlTest {
    @Mocked
    OctopusSqlRunner anyRunner;

    private void parseAndRun(String query) throws Exception {
        for (OctopusSqlCommand c : OctopusSql.parse(query))
            OctopusSql.run(c, anyRunner);
    }

    @Test
    public void testSet() throws Exception {
        final String confParam = "param_name";

        parseAndRun("SET \"" + confParam + "\" = TRUE");
        parseAndRun("SET \"" + confParam + "\" = false");
        parseAndRun("SET \"" + confParam + "\" = ON");
        parseAndRun("SET \"" + confParam + "\" = off");
        parseAndRun("SET \"" + confParam + "\" = .7");
        parseAndRun("SET \"" + confParam + "\" = 7");
        parseAndRun("SET \"" + confParam + "\" = 7.");
        parseAndRun("SET \"" + confParam + "\" = 7.7");
        parseAndRun("SET \"" + confParam + "\" = 7E+3");
        parseAndRun("SET \"" + confParam + "\" = .7e-1");
        parseAndRun("SET \"" + confParam + "\" = 'octaline'");

        new VerificationsInOrder() {{
            anyRunner.set(confParam, "TRUE");
            anyRunner.set(confParam, "false");
            anyRunner.set(confParam, "ON");
            anyRunner.set(confParam, "off");
            anyRunner.set(confParam, ".7");
            anyRunner.set(confParam, "7");
            anyRunner.set(confParam, "7.");
            anyRunner.set(confParam, "7.7");
            anyRunner.set(confParam, "7E+3");
            anyRunner.set(confParam, ".7e-1");
            anyRunner.set(confParam, "octaline");
        }};
    }

    @Test
    public void testAddDataSource() throws Exception {
        final String dataSourceName = "bitnine";
        final String connectionString = "jdbc:sqlite:file::memory:?cache=shared";
        final String driverName = "org.sqlite.JDBC";

        parseAndRun("ALTER SYSTEM ADD DATASOURCE \"" + dataSourceName + "\" CONNECT TO '" + connectionString + "' USING '" + driverName + "'");
        new Verifications() {{
            anyRunner.addDataSource(dataSourceName, connectionString, driverName);
        }};
    }

    private static Matcher<OctopusSqlObjectTarget> targetEqualTo(final OctopusSqlObjectTarget expected) {
        return new TypeSafeMatcher<OctopusSqlObjectTarget>() {
            @Override
            protected boolean matchesSafely(OctopusSqlObjectTarget actual) {
                if (expected == actual)
                    return true;
                if (expected == null)
                    return false;

                if (expected.getType() != actual.getType())
                    return false;
                if (!Objects.equals(expected.getDataSource(), actual.getDataSource()))
                    return false;
                if (!Objects.equals(expected.getSchema(), actual.getSchema()))
                    return false;
                if (!Objects.equals(expected.getTable(), actual.getTable()))
                    return false;
                if (!Objects.equals(expected.getColumn(), actual.getColumn()))
                    return false;
                if (!Objects.equals(expected.getUser(), actual.getUser()))
                    return false;

                return true;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText('<'
                        + expected.getType().name() + ','
                        + expected.getDataSource() + ','
                        + expected.getSchema() + ','
                        + expected.getTable() + ','
                        + expected.getColumn() + ','
                        + expected.getUser() + '>');
            }
        };
    }

    @Test
    public void testUpdateDataSource() throws Exception {
        final String dataSourceName = "bitnine";
        final String schemaName = "octopus";
        final String tablePattern = "table%";

        parseAndRun("ALTER SYSTEM UPDATE DATASOURCE \"" + dataSourceName + "\"");
        parseAndRun("ALTER SYSTEM UPDATE TABLE \"" + dataSourceName + "\".\""
                + schemaName + "\".'" + tablePattern + "'");

        new VerificationsInOrder() {{
            OctopusSqlObjectTarget target = new OctopusSqlObjectTarget();

            target.setType(OctopusSqlObjectTarget.Type.DATASOURCE);
            target.setDataSource(dataSourceName);
            anyRunner.updateDataSource(withArgThat(targetEqualTo(target)));

            target.setType(OctopusSqlObjectTarget.Type.TABLE);
            target.setSchema(schemaName);
            target.setTable(tablePattern);
            anyRunner.updateDataSource(withArgThat(targetEqualTo(target)));
        }};
    }

    @Test
    public void testDropDataSource() throws Exception {
        final String dataSourceName = "bitnine";
        parseAndRun("ALTER SYSTEM DROP DATASOURCE \"" + dataSourceName + "\"");
        new Verifications() {{
            anyRunner.dropDataSource(dataSourceName);
        }};
    }

    @Test
    public void testUser() throws Exception {
        final String name = "octopus";
        final String password = "bitnine";

        parseAndRun("CREATE USER \"" + name + "\" IDENTIFIED BY '" + password + "'");
        parseAndRun("ALTER USER \"" + name + "\" IDENTIFIED BY '" + password + "'");
        parseAndRun("DROP USER \"" + name + "\"");

        new Verifications() {{
            anyRunner.createUser(name, password);
            anyRunner.alterUser(name, password, (String) withNull());
            anyRunner.dropUser(name);
        }};
    }

    @Test
    public void testRole() throws Exception {
        final String name = "octopus";

        parseAndRun("CREATE ROLE \"" + name + "\"");
        parseAndRun("DROP ROLE \"" + name + "\"");

        new Verifications() {{
            anyRunner.createRole(name);
            anyRunner.dropRole(name);
        }};
    }

    private static <E> Matcher<List<E>> listEqualTo(final List<E> expected) {
        return new TypeSafeMatcher<List<E>>() {
            @Override
            protected boolean matchesSafely(List<E> actual) {
                if (expected == actual)
                    return true;
                if (expected == null)
                    return false;

                if (expected.size() != actual.size())
                    return false;
                if (!expected.containsAll(actual))
                    return false;

                return true;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText(expected.toString());
            }
        };
    }

    @Test
    public void testGrantRevokeSysPrivs() throws Exception {
        final List<SystemPrivilege> sysPrivs = Arrays.asList(SystemPrivilege.values());
        final List<String> names = Arrays.asList("octopus", "junseok");

        String sysPrivsStr = StringUtils.join(sysPrivs, ", ").replace('_', ' ') + ", ALL PRIVILEGES";
        parseAndRun("GRANT " + sysPrivsStr + " TO \"" + StringUtils.join(names, "\", \"") + "\"");

        parseAndRun("REVOKE ALL PRIVILEGES FROM \"" + names.get(0) + "\"");

        new Verifications() {{
            anyRunner.grantSystemPrivileges(withArgThat(listEqualTo(sysPrivs)), withArgThat(listEqualTo(names)));
            anyRunner.revokeSystemPrivileges(withArgThat(listEqualTo(sysPrivs)), withArgThat(listEqualTo(names.subList(0, 1))));
        }};
    }

    @Test
    public void testGrantRevokeObjPrivs() throws Exception {
        final List<ObjectPrivilege> objPrivs = Arrays.asList(ObjectPrivilege.values());
        final String[] schemaName = {"bitnine", "default"};
        final List<String> names = Arrays.asList("octopus", "junseok");

        String objPrivsStr = StringUtils.join(objPrivs, ", ").replace('_', ' ') + ", ALL";
        String schemaNameStr = "\"" + StringUtils.join(schemaName, "\".\"") + "\"";
        parseAndRun("GRANT " + objPrivsStr
                + " ON " + schemaNameStr
                + " TO \"" + StringUtils.join(names, "\", \"") + "\"");

        parseAndRun("REVOKE ALL ON " + schemaNameStr + " FROM \"" + names.get(0) + "\"");

        new Verifications() {{
            anyRunner.grantObjectPrivileges(withArgThat(listEqualTo(objPrivs)), schemaName, withArgThat(listEqualTo(names)));
            anyRunner.revokeObjectPrivileges(withArgThat(listEqualTo(objPrivs)), schemaName, withArgThat(listEqualTo(names.subList(0, 1))));
        }};
    }

    @Test
    public void testShow() throws Exception {
        parseAndRun("SHOW DATASOURCES");

        final String schemaPattern = "default_";
        parseAndRun("SHOW SCHEMAS SCHEMA '" + schemaPattern + "'");

        final String dataSourceName = "data_source";
        final String tablePattern = "table%";
        parseAndRun("SHOW TABLES DATASOURCE \"" + dataSourceName + "\" TABLE '" + tablePattern + "'");

        final String columnPattern = "%\\_col";
        parseAndRun("SHOW COLUMNS COLUMN '" + columnPattern + "'");

        parseAndRun("SHOW ALL USERS");

        final String name = "octopus";
        parseAndRun("SHOW OBJECT PRIVILEGES FOR \"" + name + "\"");

        final String commentPattern = "%\\comment";
        parseAndRun("SHOW COMMENTS '" + commentPattern + "' DATASOURCE '" + dataSourceName + "'");

        new Verifications() {{
            anyRunner.showDataSources();
            anyRunner.showSchemas((String) withNull(), schemaPattern);
            anyRunner.showTables(dataSourceName, (String) withNull(), tablePattern);
            anyRunner.showColumns((String) withNull(), (String) withNull(), (String) withNull(), columnPattern);
            anyRunner.showAllUsers();
            anyRunner.showObjPrivsFor(name);
            anyRunner.showComments(commentPattern, dataSourceName, (String) withNull(), (String) withNull(), (String) withNull());
        }};
    }

    @Test
    public void testCommentOn() throws Exception {
        final String dataSourceName = "bitnine";
        final String schemaName = "default";
        final String tableName = "employee";
        final String columnName = "permanent";
        final String userName = "junseok";
        final String comment = "comment";

        parseAndRun("COMMENT ON DATASOURCE \"" + dataSourceName + "\" IS '" + comment + "'");
        parseAndRun("COMMENT ON SCHEMA \"" + dataSourceName + "\".\"" + schemaName + "\" IS '" + comment + "'");
        parseAndRun("COMMENT ON TABLE \"" + dataSourceName + "\".\""
                + schemaName + "\".\""
                + tableName + "\" IS '" + comment + "'");
        parseAndRun("COMMENT ON COLUMN \"" + dataSourceName + "\".\""
                + schemaName + "\".\""
                + tableName + "\".\""
                + columnName + "\" IS '" + comment + "'");
        parseAndRun("COMMENT ON USER \"" + userName + "\" IS '" + comment + "'");

        new VerificationsInOrder() {{
            OctopusSqlObjectTarget target = new OctopusSqlObjectTarget();

            target.setType(OctopusSqlObjectTarget.Type.DATASOURCE);
            target.setDataSource(dataSourceName);
            anyRunner.commentOn(withArgThat(targetEqualTo(target)), comment);

            target.setType(OctopusSqlObjectTarget.Type.SCHEMA);
            target.setSchema(schemaName);
            anyRunner.commentOn(withArgThat(targetEqualTo(target)), comment);

            target.setType(OctopusSqlObjectTarget.Type.TABLE);
            target.setTable(tableName);
            anyRunner.commentOn(withArgThat(targetEqualTo(target)), comment);

            target.setType(OctopusSqlObjectTarget.Type.COLUMN);
            target.setColumn(columnName);
            anyRunner.commentOn(withArgThat(targetEqualTo(target)), comment);

            target = new OctopusSqlObjectTarget();
            target.setType(OctopusSqlObjectTarget.Type.USER);
            target.setUser(userName);
            anyRunner.commentOn(withArgThat(targetEqualTo(target)), comment);
        }};
    }

    @Test
    public void testSetDataCategoryOn() throws Exception {
        final String dataSourceName = "bitnine";
        final String schemaName = "default";
        final String tableName = "employee";
        final String columnName = "permanent";
        final String category = "public";

        parseAndRun("SET DATACATEGORY ON COLUMN \"" + dataSourceName + "\".\""
                + schemaName + "\".\""
                + tableName + "\".\""
                + columnName + "\" IS '" + category + "'");

        new Verifications() {{
            OctopusSqlObjectTarget target = new OctopusSqlObjectTarget();
            target.setType(OctopusSqlObjectTarget.Type.COLUMN);
            target.setDataSource(dataSourceName);
            target.setSchema(schemaName);
            target.setTable(tableName);
            target.setColumn(columnName);

            anyRunner.setDataCategoryOn(withArgThat(targetEqualTo(target)), category);
        }};
    }
}

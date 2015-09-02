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

import kr.co.bitnine.octopus.meta.privilege.SystemPrivilege;
import kr.co.bitnine.octopus.postgres.executor.TupleSet;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.util.*;

import static kr.co.bitnine.octopus.sql.OctopusSqlSystemPrivileges.*;

public final class OctopusSql
{
    private OctopusSql() { }

    private static class Listener extends OctopusSqlBaseListener
    {
        private List<OctopusSqlCommand> commands;

        private Set<SystemPrivilege> sysPrivs;

        // for commentOnTarget rule. FIXME
        OctopusSqlCommentOn.Target commentOnTargetType;
        OctopusSqlTargetIdentifier commentOnTarget;

        Listener()
        {
            commands = new ArrayList<>();

            sysPrivs = null;
        }

        @Override
        public void exitDdl(OctopusSqlParser.DdlContext ctx)
        {
            if (ctx.exception != null)
                throw ctx.exception;
        }

        @Override
        public void exitDataSourceClause(OctopusSqlParser.DataSourceClauseContext ctx)
        {
            String dataSourceName = ctx.dataSourceName().getText();
            String jdbcConnectionString = ctx.jdbcConnectionString().getText();
            commands.add(new OctopusSqlAddDataSource(dataSourceName, jdbcConnectionString));
        }

        @Override
        public void exitCreateUser(OctopusSqlParser.CreateUserContext ctx)
        {
            String name = ctx.user().getText();
            String password = ctx.password().getText();
            commands.add(new OctopusSqlCreateUser(name, password));
        }

        @Override
        public void exitAlterUser(OctopusSqlParser.AlterUserContext ctx)
        {
            String name = ctx.user().getText();
            String password = ctx.password().getText();
            OctopusSqlParser.OldPasswordContext oldPasswordCtx = ctx.oldPassword();
            String oldPassword = oldPasswordCtx == null ? null : oldPasswordCtx.getText();
            commands.add(new OctopusSqlAlterUser(name, password, oldPassword));
        }

        @Override
        public void exitDropUser(OctopusSqlParser.DropUserContext ctx)
        {
            String name = ctx.user().getText();
            commands.add(new OctopusSqlDropUser(name));
        }

        @Override
        public void exitCreateRole(OctopusSqlParser.CreateRoleContext ctx)
        {
            String name = ctx.role().getText();
            commands.add(new OctopusSqlCreateRole(name));
        }

        @Override
        public void exitDropRole(OctopusSqlParser.DropRoleContext ctx)
        {
            String name = ctx.role().getText();
            commands.add(new OctopusSqlDropRole(name));
        }

        @Override
        public void exitGrantSystemPrivileges(OctopusSqlParser.GrantSystemPrivilegesContext ctx)
        {
            assert this.sysPrivs != null;

            List<SystemPrivilege> sysPrivs = new ArrayList<>(this.sysPrivs);

            Set<String> grantees = new HashSet<>();
            for (OctopusSqlParser.GranteeContext grantee : ctx.grantees().grantee())
                grantees.add(grantee.getText());

            commands.add(new OctopusSqlGrantSysPrivs(sysPrivs, new ArrayList<>(grantees)));

            this.sysPrivs = null;
        }

        @Override
        public void exitRevokeSystemPrivileges(OctopusSqlParser.RevokeSystemPrivilegesContext ctx)
        {
            assert this.sysPrivs != null;

            List<SystemPrivilege> sysPrivs = new ArrayList<>(this.sysPrivs);

            Set<String> revokees = new HashSet<>();
            for (OctopusSqlParser.GranteeContext revokee : ctx.grantees().grantee())
                revokees.add(revokee.getText());

            commands.add(new OctopusSqlRevokeSysPrivs(sysPrivs, new ArrayList<>(revokees)));

            this.sysPrivs = null;
        }

        @Override
        public void enterSystemPrivileges(OctopusSqlParser.SystemPrivilegesContext ctx)
        {
            assert sysPrivs == null;
            sysPrivs = new HashSet<>();
        }

        @Override
        public void exitSysPrivAlterSystem(OctopusSqlParser.SysPrivAlterSystemContext ctx)
        {
            assert sysPrivs != null;
            sysPrivs.add(SystemPrivilege.ALTER_SYSTEM);
        }

        @Override
        public void exitSysPrivSelectAnyTable(OctopusSqlParser.SysPrivSelectAnyTableContext ctx)
        {
            assert sysPrivs != null;
            sysPrivs.add(SystemPrivilege.SELECT_ANY_TABLE);
        }

        @Override
        public void exitSysPrivCreateUser(OctopusSqlParser.SysPrivCreateUserContext ctx)
        {
            assert sysPrivs != null;
            sysPrivs.add(SystemPrivilege.CREATE_USER);
        }

        @Override
        public void exitSysPrivAlterUser(OctopusSqlParser.SysPrivAlterUserContext ctx)
        {
            assert sysPrivs != null;
            sysPrivs.add(SystemPrivilege.ALTER_USER);
        }

        @Override
        public void exitSysPrivDropUser(OctopusSqlParser.SysPrivDropUserContext ctx)
        {
            assert sysPrivs != null;
            sysPrivs.add(SystemPrivilege.DROP_USER);
        }

        @Override
        public void exitSysPrivCommentAny(OctopusSqlParser.SysPrivCommentAnyContext ctx)
        {
            assert sysPrivs != null;
            sysPrivs.add(SystemPrivilege.COMMENT_ANY);
        }

        @Override
        public void exitSysPrivGrantAnyObjPriv(OctopusSqlParser.SysPrivGrantAnyObjPrivContext ctx)
        {
            assert sysPrivs != null;
            sysPrivs.add(SystemPrivilege.GRANT_ANY_OBJECT_PRIVILEGE);
        }

        @Override
        public void exitSysPrivGrantAnyPriv(OctopusSqlParser.SysPrivGrantAnyPrivContext ctx)
        {
            assert sysPrivs != null;
            sysPrivs.add(SystemPrivilege.GRANT_ANY_PRIVILEGE);
        }

        @Override
        public void exitSysPrivAllPrivs(OctopusSqlParser.SysPrivAllPrivsContext ctx)
        {
            assert sysPrivs != null;
            sysPrivs.addAll(Arrays.asList(SystemPrivilege.values()));
        }

        @Override
        public void exitShowDataSources(OctopusSqlParser.ShowDataSourcesContext ctx)
        {
        }

        @Override
        public void exitShowSchemas(OctopusSqlParser.ShowSchemasContext ctx)
        {
        }

        @Override
        public void exitShowTables(OctopusSqlParser.ShowTablesContext ctx)
        {
            String dataSourceName = ctx.dataSourceName() == null ? null : ctx.dataSourceName().getText();
            String schemaPattern = ctx.schemaPattern() == null ? null : ctx.schemaPattern().getText();
            String tablePattern = ctx.tablePattern() == null ? null : ctx.tablePattern().getText();
            commands.add(new OctopusSqlShowTables(dataSourceName, schemaPattern, tablePattern));
        }

        @Override
        public void exitShowColumns(OctopusSqlParser.ShowColumnsContext ctx)
        {
        }

        @Override
        public void exitShowUsers(OctopusSqlParser.ShowUsersContext ctx)
        {
            commands.add(new OctopusSqlShowUsers());
        }

        @Override
        public void exitShowTablePrivileges(OctopusSqlParser.ShowTablePrivilegesContext ctx)
        {
        }

        @Override
        public void exitShowColumnPrivileges(OctopusSqlParser.ShowColumnPrivilegesContext ctx)
        {
        }

        @Override
        public void exitCommentOn(OctopusSqlParser.CommentOnContext ctx)
        {
            String comment = ctx.comment().getText();
            commands.add(new OctopusSqlCommentOn(commentOnTargetType, commentOnTarget, comment));
        }

        @Override
        public void exitCommentDataSource(OctopusSqlParser.CommentDataSourceContext ctx)
        {
            commentOnTargetType = OctopusSqlCommentOn.Target.DATASOURCE;

            commentOnTarget = new OctopusSqlTargetIdentifier();
            commentOnTarget.dataSource = ctx.dataSourceName().getText();
        }

        @Override
        public void exitCommentSchema(OctopusSqlParser.CommentSchemaContext ctx)
        {
            commentOnTargetType = OctopusSqlCommentOn.Target.SCHEMA;

            commentOnTarget = new OctopusSqlTargetIdentifier();
            commentOnTarget.dataSource = ctx.dataSourceName().getText();
            commentOnTarget.schema = ctx.schemaName().getText();
        }

        @Override
        public void exitCommentTable(OctopusSqlParser.CommentTableContext ctx)
        {
            commentOnTargetType = OctopusSqlCommentOn.Target.TABLE;

            commentOnTarget = new OctopusSqlTargetIdentifier();
            commentOnTarget.dataSource = ctx.dataSourceName().getText();
            commentOnTarget.schema = ctx.schemaName().getText();
            commentOnTarget.table = ctx.tableName().getText();
        }

        @Override
        public void exitCommentColumn(OctopusSqlParser.CommentColumnContext ctx)
        {
            commentOnTargetType = OctopusSqlCommentOn.Target.COLUMN;

            commentOnTarget = new OctopusSqlTargetIdentifier();
            commentOnTarget.dataSource = ctx.dataSourceName().getText();
            commentOnTarget.schema = ctx.schemaName().getText();
            commentOnTarget.table = ctx.tableName().getText();
            commentOnTarget.column = ctx.columnName().getText();
        }

        @Override
        public void exitCommentUser(OctopusSqlParser.CommentUserContext ctx)
        {
            commentOnTargetType = OctopusSqlCommentOn.Target.USER;

            commentOnTarget = new OctopusSqlTargetIdentifier();
            commentOnTarget.user = ctx.user().getText();
        }

        @Override
        public void exitSetDataCategoryOn(OctopusSqlParser.SetDataCategoryOnContext ctx)
        {
            String dataSource, schema, table, column, category;

            dataSource = ctx.dataSourceName().getText();
            schema = ctx.schemaName().getText();
            table = ctx.tableName().getText();
            column = ctx.columnName().getText();
            category = ctx.category().getText();

            commands.add(new OctopusSqlSetDataCategoryOn(dataSource, schema, table, column, category));
        }

        List<OctopusSqlCommand> getSqlCommands()
        {
            return commands;
        }
    }

    public static List<OctopusSqlCommand> parse(String query)
    {
        ANTLRInputStream input = new ANTLRInputStream(query);
        OctopusSqlLexer lexer = new OctopusSqlLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        OctopusSqlParser parser = new OctopusSqlParser(tokens);
        ParserRuleContext tree = parser.ddl();

        ParseTreeWalker walker = new ParseTreeWalker();
        Listener lsnr = new Listener();
        walker.walk(lsnr, tree);

        return lsnr.getSqlCommands();
    }

    public static TupleSet run(OctopusSqlCommand command, OctopusSqlRunner runner) throws Exception
    {
        switch (command.getType()) {
            case ADD_DATASOURCE:
                OctopusSqlAddDataSource addDataSource = (OctopusSqlAddDataSource) command;
                runner.addDataSource(addDataSource.getDataSourceName(), addDataSource.getJdbcConnectionString());
                break;
            case CREATE_USER:
                OctopusSqlCreateUser createUser = (OctopusSqlCreateUser) command;
                runner.createUser(createUser.getName(), createUser.getPassword());
                break;
            case ALTER_USER:
                OctopusSqlAlterUser alterUser = (OctopusSqlAlterUser) command;
                runner.alterUser(alterUser.getName(), alterUser.getPassword(), alterUser.getOldPassword());
                break;
            case DROP_USER:
                OctopusSqlDropUser dropUser = (OctopusSqlDropUser) command;
                runner.dropUser(dropUser.getName());
                break;
            case CREATE_ROLE:
                OctopusSqlCreateRole createRole = (OctopusSqlCreateRole) command;
                runner.createRole(createRole.getName());
                break;
            case DROP_ROLE:
                OctopusSqlDropRole dropRole = (OctopusSqlDropRole) command;
                runner.dropRole(dropRole.getName());
                break;
            case GRANT_SYS_PRIVS:
                OctopusSqlGrantSysPrivs grant = (OctopusSqlGrantSysPrivs) command;
                runner.grantSystemPrivileges(grant.getSysPrivs(), grant.getGrantees());
                break;
            case REVOKE_SYS_PRIVS:
                OctopusSqlRevokeSysPrivs revoke = (OctopusSqlRevokeSysPrivs) command;
                runner.revokeSystemPrivileges(revoke.getSysPrivs(), revoke.getGrantees());
                break;
            case SHOW_TABLES:
                OctopusSqlShowTables showTables = (OctopusSqlShowTables) command;
                return runner.showTables(showTables.getDataSourceName(), showTables.getSchemaPattern(), showTables.getTablePattern());
            case SHOW_USERS:
                return runner.showUsers();
            case COMMENT_ON:
                OctopusSqlCommentOn commentOn = (OctopusSqlCommentOn) command;
                runner.commentOn(commentOn.getTargetType(), commentOn.getTarget(), commentOn.getComment());
                break;
            case SET_DATACATEGORY_ON:
                OctopusSqlSetDataCategoryOn setDataCategoryOn = (OctopusSqlSetDataCategoryOn) command;
                runner.setDataCategoryOn(setDataCategoryOn.getDataSource(), setDataCategoryOn.getSchema(), setDataCategoryOn.getTable(), setDataCategoryOn.getColumn(), setDataCategoryOn.getCategory());
                break;
            default:
                throw new RuntimeException("invalid Octopus SQL command");
        }
        return null;
    }
}

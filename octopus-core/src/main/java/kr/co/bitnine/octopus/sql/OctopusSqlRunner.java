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

import java.sql.ResultSet;

public interface OctopusSqlRunner
{
    void addDataSource(String dataSourceName, String jdbcConnectionString) throws Exception;
    void createUser(String name, String password) throws Exception;
    void alterUser(String name, String password, String oldPassword) throws Exception;
    void dropUser(String name) throws Exception;
    void createRole(String role) throws Exception;
    void dropRole(String role) throws Exception;
    ResultSet showDataSources() throws Exception;
    ResultSet showSchemas(String datasource, String schemapattern) throws Exception;
    ResultSet showTables(String datasource, String schemapattern, String tablepattern) throws Exception;
    ResultSet showColumns(String datasource, String schemapattern, String tablepattern, String columnpattern) throws Exception;
    ResultSet showTablePrivileges(String datasource, String schemapattern, String tablepattern) throws Exception;
    ResultSet showColumnPrivileges(String datasource, String schemapattern, String tablepattern, String columnpattern) throws Exception;
    ResultSet showUsers() throws Exception;
}


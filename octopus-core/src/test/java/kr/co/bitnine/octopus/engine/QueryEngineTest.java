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

package kr.co.bitnine.octopus.engine;

import kr.co.bitnine.octopus.conf.OctopusConfiguration;
import kr.co.bitnine.octopus.frame.ConnectionManager;
import kr.co.bitnine.octopus.meta.MetaContext;
import kr.co.bitnine.octopus.meta.MetaStore;
import kr.co.bitnine.octopus.meta.MetaStoreService;
import kr.co.bitnine.octopus.meta.MetaStores;
import kr.co.bitnine.octopus.meta.model.MetaDataSource;
import kr.co.bitnine.octopus.schema.SchemaManager;
import kr.co.bitnine.octopus.testutils.MemoryDatabase;
import org.apache.hadoop.conf.Configuration;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public class QueryEngineTest {
    private static MemoryDatabase metaMemDb;
    private static MemoryDatabase dataMemDb;
    private static MetaStoreService metaStoreService;
    private static ConnectionManager connectionManager;
    private static SchemaManager schemaManager;
    private static MetaContext metaContext;
    private static QueryEngine queryEngine;

/*
 * Temporarily disable these test cases.
 * Currently, to execute query, session and privilege information must be needed.
 * TODO: refactor QueryEngine to remove such dependencies
 *
    @BeforeClass
    public static void setUpClass() throws Exception {
        metaMemDb = new MemoryDatabase("META");
        metaMemDb.start();

        dataMemDb = new MemoryDatabase("DATA");
        dataMemDb.start();
        dataMemDb.importJSON(QueryEngineTest.class.getClass(), "/sample.json");

        Configuration conf = new OctopusConfiguration();
        conf.set("metastore.jdo.connection.drivername", MemoryDatabase.DRIVER_NAME);
        conf.set("metastore.jdo.connection.URL", metaMemDb.connectionString);
        conf.set("metastore.jdo.connection.username", "");
        conf.set("metastore.jdo.connection.password", "");

        MetaStore metaStore = MetaStores.newInstance(conf.get("metastore.class"));
        metaStoreService = new MetaStoreService(metaStore);
        metaStoreService.init(conf);
        metaStoreService.start();

        connectionManager = new ConnectionManager(metaStore);
        connectionManager.init(conf);
        connectionManager.start();

        schemaManager = SchemaManager.getSingletonInstance(metaStore);
        schemaManager.init(conf);
        schemaManager.start();

        metaContext = metaStore.getMetaContext();

        MetaDataSource metaDataSource = metaContext.addJdbcDataSource(MemoryDatabase.DRIVER_NAME, dataMemDb.connectionString, dataMemDb.name);
        schemaManager.addDataSource(metaDataSource);

        queryEngine = new QueryEngine(metaContext, connectionManager, schemaManager);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        metaContext.close();

        schemaManager.stop();
        connectionManager.stop();
        metaStoreService.stop();

        dataMemDb.stop();
        metaMemDb.stop();
    }

    @Test
    public void testQuery() throws Exception
    {
        Portal p = queryEngine.query("SELECT ID, NAME FROM BITNINE");
        TupleSet ts = p.run(0);
        while (true) {
            Tuple t = ts.next();
            if (t == null)
                break;

            Datum[] datums = t.getDatums();
            for (int i = 0; i < datums.length; i++)
                System.out.println("  " + datums[i].out());
        }
        ts.close();
    }

    @Test
    public void testShow() throws Exception
    {
        Portal p = queryEngine.query("SHOW TABLES DATASOURCE " + dataMemDb.name + " SCHEMA '%DEFAULT'");
        TupleSet ts = p.run(0);
        while (true) {
            Tuple t = ts.next();
            if (t == null)
                break;

            Datum[] datums = t.getDatums();
            for (int i = 0; i < datums.length; i++)
                System.out.println("  " + datums[i].out());
        }
        ts.close();
    }
 */
}

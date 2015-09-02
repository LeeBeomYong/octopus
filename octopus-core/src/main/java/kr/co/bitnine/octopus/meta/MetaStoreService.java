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

package kr.co.bitnine.octopus.meta;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.service.AbstractService;

import java.util.Map;
import java.util.Properties;

public class MetaStoreService extends AbstractService
{
    private final Properties props = new Properties();
    private final MetaStore metaStore;

    public MetaStoreService(MetaStore metaStore)
    {
        super(metaStore.getClass().getName());

        this.metaStore = metaStore;
    }

    @Override
    protected final void serviceInit(Configuration conf)
    {
        props.clear();
        for (Map.Entry e : conf)
            props.put(e.getKey(), e.getValue());
    }

    @Override
    protected final void serviceStart() throws Exception
    {
        metaStore.start(props);
        MetaStores.initialize(metaStore);

        super.serviceStart();
    }

    @Override
    protected final void serviceStop() throws Exception
    {
        metaStore.stop();

        super.serviceStop();
    }
}

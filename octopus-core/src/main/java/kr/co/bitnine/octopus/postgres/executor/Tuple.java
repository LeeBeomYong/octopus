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

package kr.co.bitnine.octopus.postgres.executor;

import kr.co.bitnine.octopus.postgres.utils.adt.Datum;

public class Tuple
{
    private final Datum[] datums;

    public Tuple(int numAttrs)
    {
        datums = new Datum[numAttrs];
    }

    public void setDatum(int index, Datum datum)
    {
        datums[index] = datum;
    }

    public Datum[] getDatums()
    {
        return datums;
    }

    public Datum getDatum(int index)
    {
        return datums[index];
    }
}

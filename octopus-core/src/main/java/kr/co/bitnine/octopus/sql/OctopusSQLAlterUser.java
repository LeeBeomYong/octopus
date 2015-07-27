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

/**
 * Created by Kimbyungmoon on 15. 7. 20..
 */
public class OctopusSqlAlterUser extends OctopusSqlCommand{
    private String name;
    private String password;
    private String old_password;

    OctopusSqlAlterUser(String name, String password, String old_password)
    {
        this.name = name;
        this.password = password;
        this.old_password = old_password;
    }

    String getName()
    {
        return name;
    }

    String getPassword()
    {
        return password;
    }

    String getOld_password()
    {
        return this.old_password;
    }

    @Override
    public OctopusSqlCommand.Type getType()
    {
        return OctopusSqlCommand.Type.ALTER_USER;
    }
}

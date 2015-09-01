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

public class OctopusSqlCommentOn extends OctopusSqlCommand {
    public enum Target
    {
        DATASOURCE,
        SCHEMA,
        TABLE,
        COLUMN,
        USER
    }

    private Target targetType;
    private String target;
    private String comment;

    public OctopusSqlCommentOn (Target targetType, String target, String comment) {
        this.targetType = targetType;
        this.target = target;
        this.comment = comment;
    }

    public Target getTargetType() {
        return this.targetType;
    }

    public String getTarget() {
        return this.target;
    }

    public String getComment() {
        return this.comment;
    }

    @Override
    public OctopusSqlCommand.Type getType()
    {
        return Type.COMMENT_ON;
    }
}

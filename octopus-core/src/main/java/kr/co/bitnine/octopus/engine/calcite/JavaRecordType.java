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

package kr.co.bitnine.octopus.engine.calcite;

import com.google.common.base.Preconditions;
import java.util.List;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rel.type.RelRecordType;

/**
 * Record type based on a Java class. The fields of the type are the fields
 * of the class.
 * <p/>
 * <p><strong>NOTE: This class is experimental and subject to
 * change/removal without notice</strong>.</p>
 */
public final class JavaRecordType extends RelRecordType {
    private final Class clazz;

    public JavaRecordType(List<RelDataTypeField> fields, Class clazz) {
        super(fields);
        this.clazz = Preconditions.checkNotNull(clazz);
    }

    public Class getClazz() {
        return clazz;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj
                || obj instanceof JavaRecordType
                && fieldList.equals(((JavaRecordType) obj).fieldList)
                && clazz == ((JavaRecordType) obj).clazz;
    }

    @Override
    public int hashCode() {
        return fieldList.hashCode() ^ clazz.hashCode();
    }
}

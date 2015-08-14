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

import kr.co.bitnine.octopus.postgres.utils.FormatCode;

/*
 * portal
 */
public class ExecutableStatement
{
    private final ParsedStatement parsedStatement;
    private final FormatCode[] paramFormats;
    private final byte[][] paramValues;
    private final FormatCode[] resultFormats;

    public ExecutableStatement(ParsedStatement parsedStatement, FormatCode[] paramFormats, byte[][] paramValues, FormatCode[] resultFormats)
    {
        this.parsedStatement = parsedStatement;
        this.paramFormats = paramFormats;
        this.paramValues = paramValues;
        this.resultFormats = resultFormats;
    }

    public ParsedStatement getParsedStatement()
    {
        return parsedStatement;
    }
}

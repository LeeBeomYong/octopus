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

package kr.co.bitnine.octopus.postgres.libpq;

public final class ProtocolConstants {
    public static final int CANCEL_REQUEST_CODE = (1234 << 16) | 5678;
    public static final int SSL_REQUEST_CODE = (1234 << 16) | 5679;

    public static final int PROTOCOL_LATEST = protocolVersion(3, 0);

    public static final String POSTGRES_VERSION = "9.4.4";

    private ProtocolConstants() { }

    public static int protocolMajor(int version) {
        return version >> 16;
    }

    public static int protocolMinor(int version) {
        return version & 0x0000ffff;
    }

    public static int protocolVersion(int major, int minor) {
        return (major << 16) | minor;
    }
}

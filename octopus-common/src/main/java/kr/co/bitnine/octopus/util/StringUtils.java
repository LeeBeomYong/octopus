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

package kr.co.bitnine.octopus.util;

import org.apache.commons.lang.SystemUtils;
import org.apache.commons.logging.Log;
import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceStability;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.util.ShutdownHookManager;
import org.apache.hadoop.util.SignalLogger;

import java.util.Arrays;

/**
 * General string utils
 */
@InterfaceAudience.Private
@InterfaceStability.Unstable
public final class StringUtils {
    /**
     * Priority of the StringUtils shutdown hook.
     */
    public static final int SHUTDOWN_HOOK_PRIORITY = 0;

    private StringUtils() { }

    /**
     * Return a message for logging.
     *
     * @param prefix prefix keyword for the message
     * @param msg    content of the message
     * @return a message for logging
     */
    private static String toStartupShutdownString(String prefix, String[] msg) {
        StringBuilder sb = new StringBuilder(prefix);
        sb.append("\n/************************************************************");
        for (String s : msg)
            sb.append("\n").append(prefix).append(s);
        sb.append("\n************************************************************/");
        return sb.toString();
    }

    /**
     * Print a log message for starting up and shutting down
     *
     * @param clazz the class of the server
     * @param args  arguments
     * @param log   the target log object
     */
    public static void startupShutdownMessage(Class<?> clazz, String[] args,
                                              final Log log) {
        final String classname = clazz.getSimpleName();
        final String hostname = NetUtils.getHostname();

        final String build = VersionInfo.getUrl()
                + ", rev. " + VersionInfo.getRevision()
                + "; compiled by '" + VersionInfo.getUser()
                + "' on " + VersionInfo.getDate();
        String[] msg = new String[] {
            "Starting " + classname,
            "  host = " + hostname,
            "  args = " + Arrays.asList(args),
            "  version = " + VersionInfo.getVersion(),
            "  classpath = " + System.getProperty("java.class.path"),
            "  build = " + build,
            "  java = " + System.getProperty("java.version")
        };
        log.info(toStartupShutdownString("STARTUP_MSG: ", msg));

        if (SystemUtils.IS_OS_UNIX) {
            try {
                SignalLogger.INSTANCE.register(log);
            } catch (Throwable t) {
                log.warn("failed to register any UNIX signal loggers: ", t);
            }
        }
        ShutdownHookManager.get().addShutdownHook(
                new Runnable() {
                    @Override
                    public void run() {
                        log.info(toStartupShutdownString("SHUTDOWN_MSG: ",
                                new String[] {"Shutting down " + classname + " at " + hostname}));
                    }
                }, SHUTDOWN_HOOK_PRIORITY);
    }
}

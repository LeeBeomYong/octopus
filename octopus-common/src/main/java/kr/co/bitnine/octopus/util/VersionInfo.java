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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceStability;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.util.ClassUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * This class returns build information about Octopus components.
 */
@InterfaceAudience.Private
@InterfaceStability.Unstable
public class VersionInfo
{
    private static final Log LOG = LogFactory.getLog(VersionInfo.class);

    private Properties info;

    protected VersionInfo(String component)
    {
        info = new Properties();
        String versionInfoFile = component + "-version-info.properties";
        InputStream is = null;
        try {
            is = Thread.currentThread().getContextClassLoader().getResourceAsStream(versionInfoFile);
            if (is == null)
                throw new IOException("Resource not found");
            info.load(is);
        } catch (IOException e) {
            LOG.warn("Could not read '" + versionInfoFile + "', " + e.toString(), e);
        } finally {
            IOUtils.closeStream(is);
        }
    }

    protected String _getVersion()
    {
        return info.getProperty("version", "Unknown");
    }

    protected String _getUrl()
    {
        return info.getProperty("url", "Unknown");
    }

    protected String _getBranch()
    {
        return info.getProperty("branch", "Unknown");
    }

    protected String _getRevision()
    {
        return info.getProperty("revision", "Unknown");
    }

    protected String _getUser()
    {
        return info.getProperty("user", "Unknown");
    }

    protected String _getDate()
    {
        return info.getProperty("date", "Unknown");
    }

    protected String _getSrcChecksum()
    {
        return info.getProperty("srcChecksum", "Unknown");
    }

    private static VersionInfo OCTOPUS_VERSION_INFO = new VersionInfo("octopus");

    /**
     * Get the Octopus version.
     * @return the Octopus version string, eg. "0.1.0-SNAPSHOT"
     */
    public static String getVersion()
    {
        return OCTOPUS_VERSION_INFO._getVersion();
    }

    /**
     * Get the SCM URL for the root Octopus directory.
     */
    public static String getUrl()
    {
        return OCTOPUS_VERSION_INFO._getUrl();
    }

    /**
     * Get the branch on which this originated.
     * @return The branch name.
     */
    public static String getBranch()
    {
        return OCTOPUS_VERSION_INFO._getBranch();
    }

    /**
     * Get the SCM revision number for the root directory
     * @return the revision number.
     */
    public static String getRevision()
    {
        return OCTOPUS_VERSION_INFO._getRevision();
    }

    /**
     * The user that compiled Octopus.
     * @return the username of the user
     */
    public static String getUser()
    {
        return OCTOPUS_VERSION_INFO._getUser();
    }

    /**
     * The date that Octopus was compiled.
     * @return the compilation date in unix date format
     */
    public static String getDate()
    {
        return OCTOPUS_VERSION_INFO._getDate();
    }

    /**
     * Get the checksum of the source files from which Octopus was built.
     */
    public static String getSrcChecksum()
    {
        return OCTOPUS_VERSION_INFO._getSrcChecksum();
    }

    public static void main(String[] args)
    {
        System.out.println("Octopus " + getVersion());
        System.out.println("SCM: " + getUrl() + ", revision: " + getRevision());
        System.out.println("Compiled by " + getUser() + " on " + getDate());
        System.out.println("From source with checksum " + getSrcChecksum());
        System.out.println("This command was run using " + ClassUtil.findContainingJar(VersionInfo.class));
    }
}

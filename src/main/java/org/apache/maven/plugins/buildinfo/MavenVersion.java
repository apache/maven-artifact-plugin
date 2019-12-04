package org.apache.maven.plugins.buildinfo;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.cli.MavenCli;
import org.codehaus.plexus.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

/**
 * Helper to calculate the full Maven version string, as done in Maven Core
 * in CLIReportingUtils.
 * @see org.apache.maven.cli.CLIReportingUtils
 */
public class MavenVersion
{
    static String createMavenVersionString()
    {
        return createMavenVersionString( getBuildProperties() );
    }

    /**
     * Create a human readable string containing the Maven version, buildnumber, and time of build
     *
     * @param buildProperties The build properties
     * @return Readable build info
     */
    private static String createMavenVersionString( Properties buildProperties )
    {
        String timestamp = reduce( buildProperties.getProperty( "timestamp" ) );
        String version = reduce( buildProperties.getProperty( "version" ) );
        String rev = reduce( buildProperties.getProperty( "buildNumber" ) );
        String distributionName = reduce( buildProperties.getProperty( "distributionName" ) );

        String msg = distributionName + " ";
        msg += ( version != null ? version : "<version unknown>" );
        if ( rev != null || timestamp != null )
        {
            msg += " (";
            msg += ( rev != null ? rev : "" );
            if ( StringUtils.isNotBlank( timestamp ) )
            {
                String ts = formatTimestamp( Long.valueOf( timestamp ) );
                msg += ( rev != null ? "; " : "" ) + ts;
            }
            msg += ")";
        }
        return msg;
    }

    private static String reduce( String s )
    {
        return ( s != null ? ( s.startsWith( "${" ) && s.endsWith( "}" ) ? null : s ) : null );
    }

    private static Properties getBuildProperties()
    {
        Properties properties = new Properties();

        try ( InputStream resourceAsStream = MavenCli.class.getResourceAsStream(
                "/org/apache/maven/messages/build.properties" ) )
        {

            if ( resourceAsStream != null )
            {
                properties.load( resourceAsStream );
            }
        }
        catch ( IOException e )
        {
            System.err.println( "Unable determine version from JAR file: " + e.getMessage() );
        }

        return properties;
    }

    public static String formatTimestamp( long timestamp )
    {
        SimpleDateFormat sdf = new SimpleDateFormat( "yyyy-MM-dd'T'HH:mm:ssXXX" );
        return sdf.format( new Date( timestamp ) );
    }
}

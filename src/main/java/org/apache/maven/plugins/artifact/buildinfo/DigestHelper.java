package org.apache.maven.plugins.artifact.buildinfo;

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

import org.apache.commons.codec.binary.Hex;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Helper to calculate sha512.
 */
class DigestHelper
{
    static String calculateSha512( File file )
            throws MojoExecutionException
    {
        try ( FileInputStream fis = new FileInputStream( file ) )
        {
            MessageDigest messageDigest = MessageDigest.getInstance( "sha-512" );

            byte[] buffer = new byte[16 * 1024];
            int size = fis.read( buffer, 0, buffer.length );
            while ( size >= 0 )
            {
                messageDigest.update( buffer, 0, size );
                size = fis.read( buffer, 0, buffer.length );
            }

            return Hex.encodeHexString( messageDigest.digest() );
        }
        catch ( IOException ioe )
        {
            throw new MojoExecutionException( "Error opening file " + file, ioe );
        }
        catch ( NoSuchAlgorithmException nsae )
        {
            throw new MojoExecutionException( "Could not get hash algorithm", nsae );
        }
    }
}

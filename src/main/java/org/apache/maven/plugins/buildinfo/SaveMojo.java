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

import org.apache.commons.codec.binary.Hex;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Save buildinfo file, as specified in
 * <a href="https://reproducible-builds.org/docs/jvm/">Reproducible Builds for the JVM</a>.
 */
@Mojo( name = "save", defaultPhase = LifecyclePhase.VERIFY )
public class SaveMojo
    extends AbstractMojo
{
    /**
     * The Maven project.
     */
    @Parameter( defaultValue = "${project}", readonly = true )
    protected MavenProject project;

    /**
     * The reactor projects.
     */
    @Parameter( defaultValue = "${reactorProjects}", required = true, readonly = true )
    protected List<MavenProject> reactorProjects;

    /**
     * Location of the buildinfo file.
     */
    @Parameter( defaultValue = "${project.build.directory}/buildinfo", required = true )
    private File buildinfoFile;

    public void execute() throws MojoExecutionException
    {
        try ( PrintWriter p = new PrintWriter( new BufferedWriter( new FileWriter( buildinfoFile ) ) ) )
        {
            p.println( "buildinfo.version=1.0-SNAPSHOT" );
            p.println();
            p.println( "name=" + project.getName() );
            p.println( "group-id=" + project.getGroupId() );
            p.println( "artifact-id=" + project.getArtifactId() );
            p.println( "version=" + project.getVersion() );
            p.println();
            p.println( "# source information" );
            p.println( "# TBD source.* artifact, url, scm.uri, scm.tag: what part is automatic or parameter?" );
            p.println();
            p.println( "# build instructions" );
            p.println( "build-tool=mvn" );
            p.println( "# optional build setup url, as plugin parameter" );
            p.println();
            p.println( "# effective build environment information" );
            p.println( "java.version=" + System.getProperty( "java.version" ) );
            p.println( "java.vendor=" + System.getProperty( "java.vendor" ) );
            p.println( "os.name=" + System.getProperty( "os.name" ) );
            p.println( "os.arch=" + System.getProperty( "os.arch" ) );
            p.println( "os.version=" + System.getProperty( "os.version" ) );
            p.println( "source.used= TBD" );
            p.println();
            p.println( "# Maven rebuild instructions and effective environment" );
            p.println( "mvn.rebuild-args=package" );
            p.println( "mvn.version=" + MavenVersion.createMavenVersionString() );
            p.println();
            printOutput( p );

            getLog().info( "Saved info on build to " + buildinfoFile );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Error creating file " + buildinfoFile, e );
        }
    }

    private void printOutput( PrintWriter p )
            throws MojoExecutionException
    {
        p.println( "# output" );

        printArtifact( p, 0, project.getArtifact() );

        int n = 1;
        for ( Artifact attached : project.getAttachedArtifacts() )
        {
            printArtifact( p, n++, attached );
        }
    }

    private void printArtifact( PrintWriter p, int i, Artifact artifact )
            throws MojoExecutionException
    {
        File file = artifact.getFile();
        p.println( "outputs." + i + ".filename=" + file.getName() );
        p.println( "outputs." + i + ".length=" + file.length() );
        p.println( "outputs." + i + ".checksums.sha512=" + calculateSha512( file ) );
    }

    private String calculateSha512( File file )
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

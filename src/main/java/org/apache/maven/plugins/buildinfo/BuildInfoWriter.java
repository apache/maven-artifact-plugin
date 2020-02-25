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

import java.io.File;
import java.io.PrintWriter;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

/**
 * Buildinfo content writer.
 */
public class BuildInfoWriter
{
    private final Log log;
    private final PrintWriter p;
    private final boolean mono;
    private int projectCount = -1;

    BuildInfoWriter( Log log, PrintWriter p, boolean mono )
    {
        this.log = log;
        this.p = p;
        this.mono = mono;
    }

    public void printHeader( MavenProject project )
    {
        p.println( "# https://reproducible-builds.org/docs/jvm/" );
        p.println( "buildinfo.version=1.0-SNAPSHOT" );
        p.println();
        p.println( "name=" + project.getName() );
        p.println( "group-id=" + project.getGroupId() );
        p.println( "artifact-id=" + project.getArtifactId() );
        p.println( "version=" + project.getVersion() );
        p.println();
        printSourceInformation( project );
        p.println();
        p.println( "# build instructions" );
        p.println( "build-tool=mvn" );
        //p.println( "# optional build setup url, as plugin parameter" );
        p.println();
        p.println( "# effective build environment information" );
        p.println( "java.version=" + System.getProperty( "java.version" ) );
        p.println( "java.vendor=" + System.getProperty( "java.vendor" ) );
        p.println( "os.name=" + System.getProperty( "os.name" ) );
        p.println();
        p.println( "# Maven rebuild instructions and effective environment" );
        //p.println( "mvn.rebuild-args=" + rebuildArgs );
        p.println( "mvn.version=" + MavenVersion.createMavenVersionString() );
        if ( ( project.getPrerequisites() != null ) && ( project.getPrerequisites().getMaven() != null ) )
        {
            // TODO wrong algorithm, should reuse algorithm written in versions-maven-plugin
            p.println( "mvn.minimum.version=" + project.getPrerequisites().getMaven() );
        }
        p.println();
        p.println( "# " + ( mono ? "" : "aggregated " ) + "output" );
    }

    private void printSourceInformation( MavenProject project )
    {
        boolean sourceAvailable = false;
        p.println( "# source information" );
        //p.println( "# TBD source.* artifact, url should be parameters" );
        if ( project.getScm() != null )
        {
            sourceAvailable = true;
            p.println( "source.scm.uri=" + project.getScm().getConnection() );
            p.println( "source.scm.tag=" + project.getScm().getTag() );
            if ( project.getArtifact().isSnapshot() )
            {
                log.warn( "SCM source tag in buildinfo source.scm.tag=" + project.getScm().getTag()
                    + " does not permit rebuilders reproducible source checkout" );
                // TODO is it possible to use Scm API to get SCM version?
            }
        }
        else
        {
            p.println( "# no scm configured in pom.xml" );
        }

        if ( !sourceAvailable )
        {
            log.warn( "No source information available in buildinfo for rebuilders..." );
        }
    }

    public void printArtifacts( MavenProject project )
        throws MojoExecutionException
    {
        if ( project.getArtifact() == null )
        {
            return;
        }

        String prefix = "outputs.";
        if ( !mono )
        {
            // aggregated buildinfo output
            projectCount++;
            prefix += projectCount + ".";
            p.println();
            p.println( prefix + "coordinates=" + project.getGroupId() + ':' + project.getArtifactId() );
        }

        int n = 0;
        if ( project.getArtifact().getFile() != null )
        {
            printArtifact( prefix, n++, project.getArtifact() );
        }

        for ( Artifact attached : project.getAttachedArtifacts() )
        {
            if ( attached.getType().endsWith( ".asc" ) )
            {
                // ignore pgp signatures
                continue;
            }
            if ( "javadoc".equals( attached.getClassifier() ) )
            {
                // TEMPORARY ignore javadoc, waiting for MJAVADOC-627 in m-javadoc-p 3.2.0
                continue;
            }
            printArtifact( prefix, n++, attached );
        }
    }

    private void printArtifact( String prefix, int i, Artifact artifact )
        throws MojoExecutionException
    {
        File file = artifact.getFile();
        p.println();
        p.println( prefix + i + ".filename=" + file.getName() );
        p.println( prefix + i + ".length=" + file.length() );
        p.println( prefix + i + ".checksums.sha512=" + DigestHelper.calculateSha512( file ) );
    }
}

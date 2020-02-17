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

import org.apache.commons.codec.Charsets;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.List;

/**
 * Save buildinfo file, as specified in
 * <a href="https://reproducible-builds.org/docs/jvm/">Reproducible Builds for the JVM</a>
 * for mono-module build, and exended for multi-module build.
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
     * Location of the generated buildinfo file.
     */
    @Parameter( defaultValue = "${project.build.directory}/${project.artifactId}-${project.version}.buildinfo",
                    required = true, readonly = true )
    private File buildinfoFile;

    /**
     * Specifies whether to attach the generated buildinfo file to the project.
     */
    @Parameter( property = "buildinfo.attach", defaultValue = "true" )
    private boolean attach;

    /**
     * Rebuild arguments.
     */
    //@Parameter( property = "buildinfo.rebuild-args", defaultValue = "-DskipTests verify" )
    //private String rebuildArgs;

    /**
     * Used for attaching the buildinfo file in the project.
     */
    @Component
    private MavenProjectHelper projectHelper;

    public void execute() throws MojoExecutionException
    {
        boolean mono = reactorProjects.size() == 1;

        if ( !mono )
        {
            MavenProject aggregate = reactorProjects.get( reactorProjects.size() - 1 );
            if  ( project != aggregate )
            {
                getLog().info( "Skip intermediate buildinfo, aggregate will be " + aggregate.getArtifactId() );
                return;
            }
        }

        generateBuildinfo( mono );

        if ( attach )
        {
            projectHelper.attachArtifact( project, "buildinfo", buildinfoFile );
        }
        else
        {
            getLog().info( "NOT adding buildinfo to the list of attached artifacts." );
        }
    }

    private void generateBuildinfo( boolean mono )
            throws MojoExecutionException
    {
        MavenProject root = mono ? project : getExecutionRoot();

        buildinfoFile.getParentFile().mkdirs();

        try ( PrintWriter p = new PrintWriter( new BufferedWriter(
                new OutputStreamWriter( new FileOutputStream( buildinfoFile ), Charsets.ISO_8859_1 ) ) ) )
        {
            printHeader( p, root );

            // artifact(s) fingerprints
            if ( mono )
            {
                if ( project.getArtifact() != null )
                {
                    p.println();
                    p.println( "# output" );
                    printOutput( p, project, -1 );
                }
            }
            else
            {
                int n = 0;
                for ( MavenProject project : reactorProjects )
                {
                    if ( project.getArtifact() != null )
                    {
                        p.println();
                        printOutput( p, project, n++ );
                    }
                }
            }

            getLog().info( "Saved " + ( mono ? "" : "aggregate " ) + "info on build to " + buildinfoFile );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Error creating file " + buildinfoFile, e );
        }
    }

    private void printHeader( PrintWriter p, MavenProject project )
    {
        p.println( "# https://reproducible-builds.org/docs/jvm/" );
        p.println( "buildinfo.version=1.0-SNAPSHOT" );
        p.println();
        p.println( "name=" + project.getName() );
        p.println( "group-id=" + project.getGroupId() );
        p.println( "artifact-id=" + project.getArtifactId() );
        p.println( "version=" + project.getVersion() );
        p.println();
        printSourceInformation( p, project );
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
    }

    private void printSourceInformation( PrintWriter p, MavenProject project )
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
                getLog().warn( "SCM source tag in buildinfo source.scm.tag=" + project.getScm().getTag()
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
            getLog().warn( "No source information available in buildinfo for rebuilders..." );
        }
    }

    private void printOutput( PrintWriter p, MavenProject project, int projectIndex )
            throws MojoExecutionException
    {
        String prefix = "outputs.";
        if ( projectIndex >= 0 )
        {
            // aggregated buildinfo output
            prefix += projectIndex + ".";
            p.println( prefix + "coordinates=" + project.getGroupId() + ':' + project.getArtifactId() );
        }
        int n = 0;
        if ( project.getArtifact().getFile() != null )
        {
            printArtifact( p, prefix, n++, project.getArtifact() );
        }

        for ( Artifact attached : project.getAttachedArtifacts() )
        {
            if ( attached.getType().endsWith( ".asc" ) )
            {
                // ignore pgp signatures
                continue;
            }
            if ( attached.getType().equals( "buildinfo" ) )
            {
                // ignore buildinfo files (during aggregate)
                continue;
            }
            if ( "javadoc".equals( attached.getClassifier() ) )
            {
                // TEMPORARY ignore javadoc, waiting for MJAVADOC-627 in m-javadoc-p 3.2.0
                continue;
            }
            printArtifact( p, prefix, n++, attached );
        }
    }

    private void printArtifact( PrintWriter p, String prefix, int i, Artifact artifact )
            throws MojoExecutionException
    {
        File file = artifact.getFile();
        p.println( prefix + i + ".filename=" + file.getName() );
        p.println( prefix + i + ".length=" + file.length() );
        p.println( prefix + i + ".checksums.sha512=" + DigestHelper.calculateSha512( file ) );
    }

    private MavenProject getExecutionRoot()
    {
        for ( MavenProject p : reactorProjects )
        {
            if ( p.isExecutionRoot() )
            {
                return p;
            }
        }
        return null;
    }
}

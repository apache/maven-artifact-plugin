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
     * Location of the generated buildinfo file.
     */
    @Parameter( defaultValue = "${project.build.directory}/${project.artifactId}-${project.version}.buildinfo",
                    required = true, readonly = true )
    private File buildinfoFile;

    /**
     * Location of the eventually generated aggregate buildinfo file.
     */
    @Parameter( defaultValue = "${project.build.directory}/${project.artifactId}-${project.version}"
        + "-aggregate.buildinfo", required = true, readonly = true )
    private File aggregateBuildinfoFile;

    /**
     * Specifies whether to attach the generated buildinfo file to the project.
     */
    @Parameter( property = "buildinfo.attach", defaultValue = "true" )
    private boolean attach;

    /**
     * Rebuild arguments.
     */
    @Parameter( property = "buildinfo.rebuild-args", defaultValue = "-DskipTests verify" )
    private String rebuildArgs;

    /**
     * Used for attaching the buildinfo file in the project.
     */
    @Component
    private MavenProjectHelper projectHelper;

    public void execute() throws MojoExecutionException
    {
        generateBuildinfo();

        if ( attach )
        {
            projectHelper.attachArtifact( project, "buildinfo", buildinfoFile );
        }
        else
        {
            getLog().info( "NOT adding buildinfo to the list of attached artifacts." );
        }

        if ( ( reactorProjects.size() > 1 ) && ( project == reactorProjects.get( reactorProjects.size() - 1 ) ) )
        {
            File aggregate = generateAggregateBuildinfo();

            if ( attach )
            {
                projectHelper.attachArtifact( project, "buildinfo", "aggregate", aggregate );
            }
            else
            {
                getLog().info( "NOT adding buildinfo to the list of attached artifacts." );
            }
        }
    }

    private void generateBuildinfo()
            throws MojoExecutionException
    {
        buildinfoFile.getParentFile().mkdirs();
        try ( PrintWriter p = new PrintWriter( new BufferedWriter(
                new OutputStreamWriter( new FileOutputStream( buildinfoFile ), Charsets.ISO_8859_1 ) ) ) )
        {
            printHeader( p, project );
            p.println();
            if ( project.isExecutionRoot() )
            {
                printRootInformation( p, project );
            }
            else
            {
                // multi-module non execution root
                p.println( "# build instructions" );
                p.println( "build-tool=mvn" );
                MavenProject root = getExecutionRoot();
                p.println( "mvn.build-root=" + root.getGroupId() + ':' + root.getArtifactId() + ':'
                    + root.getVersion() );
            }

            if ( project.getArtifact() != null )
            {
                p.println();
                p.println( "# output" );
                printOutput( p, project );
            }

            getLog().info( "Saved info on build to " + buildinfoFile );
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
    }

    private void printRootInformation( PrintWriter p, MavenProject project )
    {
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
        p.println( "mvn.rebuild-args=" + rebuildArgs );
        p.println( "mvn.version=" + MavenVersion.createMavenVersionString() );
        if ( ( project.getPrerequisites() != null ) && ( project.getPrerequisites().getMaven() != null ) )
        {
            // TODO wrong algorithm, should reuse algorithm written in versions-maven-plugin
            p.println( "mvn.minimum.version=" + project.getPrerequisites().getMaven() );
        }
        if ( reactorProjects.size() > 1 )
        {
            MavenProject aggregate = reactorProjects.get( reactorProjects.size() - 1 );
            p.println( "mvn.aggregate-buildinfo=" + aggregate.getGroupId() + ':' + aggregate.getArtifactId() + ':'
                + aggregate.getVersion() );
        }
    }

    private void printSourceInformation( PrintWriter p, MavenProject project )
    {
        boolean sourceAvailable = false;
        p.println( "# source information" );
        p.println( "# TBD source.* artifact, url should be parameters" );
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

    private void printOutput( PrintWriter p, MavenProject project )
            throws MojoExecutionException
    {
        int n = 0;
        if ( project.getArtifact().getFile() != null )
        {
            printArtifact( p, n++, project.getArtifact() );
        }

        for ( Artifact attached : project.getAttachedArtifacts() )
        {
            if ( attached.getType().endsWith( ".asc" ) )
            {
                // ignore pgp signatures
                continue;
            }
            if ( attached.getFile().getName().equals( "buildinfo" ) )
            {
                // ignore buildinfo files (during aggregate)
                continue;
            }
            if ( "javadoc".equals( attached.getClassifier() ) )
            {
                // TEMPORARY ignore javadoc, waiting for MJAVADOC-627 in m-javadoc-p 3.2.0
                continue;
            }
            printArtifact( p, n++, attached );
        }
    }

    private void printArtifact( PrintWriter p, int i, Artifact artifact )
            throws MojoExecutionException
    {
        File file = artifact.getFile();
        p.println( "outputs." + i + ".filename=" + file.getName() );
        p.println( "outputs." + i + ".length=" + file.length() );
        p.println( "outputs." + i + ".checksums.sha512=" + DigestHelper.calculateSha512( file ) );
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

    private File generateAggregateBuildinfo()
        throws MojoExecutionException
    {
        MavenProject root = getExecutionRoot();
        try ( PrintWriter p =
            new PrintWriter( new BufferedWriter( new OutputStreamWriter( new FileOutputStream( aggregateBuildinfoFile ),
                                                                         Charsets.ISO_8859_1 ) ) ) )
        {
            printHeader( p, root );
            p.println();
            printRootInformation( p, root );
            p.println( "mvn.build-root=" + root.getGroupId() + ':' + root.getArtifactId() + ':' + root.getVersion() );

            int n = 1;
            for ( MavenProject project : reactorProjects )
            {
                if ( project.getArtifact() != null )
                {
                    p.println();
                    p.println( "# " + n++ + '/' + reactorProjects.size() + ' ' + project.getGroupId() + ':'
                        + project.getArtifactId() );
                    printOutput( p, project );
                }
            }

            getLog().info( "Saved aggregate info on build to " + aggregateBuildinfoFile );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Error creating file " + aggregateBuildinfoFile, e );
        }
        return aggregateBuildinfoFile;
    }
}

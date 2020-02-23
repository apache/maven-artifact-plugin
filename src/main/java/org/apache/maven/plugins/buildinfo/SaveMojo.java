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
 * for mono-module build, and extended for multi-module build.
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

    public void execute()
        throws MojoExecutionException
    {
        boolean mono = reactorProjects.size() == 1;

        if ( !mono )
        {
            // if multi-module build, generate (aggregate) buildinfo only in last module
            MavenProject aggregate = reactorProjects.get( reactorProjects.size() - 1 );
            if  ( project != aggregate )
            {
                getLog().info( "Skip intermediate buildinfo, aggregate will be " + aggregate.getArtifactId() );
                return;
            }
        }

        generateBuildinfo( mono );
        getLog().info( "Saved " + ( mono ? "" : "aggregate " ) + "info on build to " + buildinfoFile );

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
            BuildInfoWriter bi = new BuildInfoWriter( getLog(), p, mono );

            bi.printHeader( root );

            // artifact(s) fingerprints
            if ( mono )
            {
                bi.printArtifacts( project );
            }
            else
            {
                for ( MavenProject project : reactorProjects )
                {
                    bi.printArtifacts( project );
                }
            }
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Error creating file " + buildinfoFile, e );
        }
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

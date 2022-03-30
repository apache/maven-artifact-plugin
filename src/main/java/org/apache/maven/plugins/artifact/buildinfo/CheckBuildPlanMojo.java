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

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.LifecycleExecutor;
import org.apache.maven.lifecycle.MavenExecutionPlan;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Check from buildplan that plugins used don't have known reproducible builds issues.
 */
@Mojo( name = "check-buildplan", threadSafe = true, requiresProject = true )
public class CheckBuildPlanMojo
    extends AbstractMojo
{
    @Parameter( defaultValue = "${project}", readonly = true )
    private MavenProject project;

    @Parameter( defaultValue = "${session}", readonly = true )
    private MavenSession session;

    @Component
    private LifecycleExecutor lifecycleExecutor;

    /** Allow to specify which goals/phases will be used to calculate execution plan. */
    @Parameter( property = "buildplan.tasks", defaultValue = "deploy" )
    private String[] tasks;

    protected MavenExecutionPlan calculateExecutionPlan()
        throws MojoExecutionException
    {
        try
        {
            return lifecycleExecutor.calculateExecutionPlan( session, tasks );
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Cannot calculate Maven execution plan" + e.getMessage(), e );
        }
    }

    @Override
    public void execute()
        throws MojoExecutionException
    {
        Properties issues = loadIssues();

        MavenExecutionPlan plan = calculateExecutionPlan();

        Set<String> plugins = new HashSet<>();
        boolean fail = false;
        for ( MojoExecution exec : plan.getMojoExecutions() )
        {
            Plugin plugin = exec.getPlugin();
            String id = plugin.getId();

            if ( plugins.add( id ) )
            {
                // check reproducibility status
                String issue = issues.getProperty( plugin.getKey() );
                if ( issue == null )
                {
                    getLog().info( "no known issue with " + id );
                }
                else if ( issue.startsWith( "fail:" ) )
                {
                    getLog().warn( "plugin without solution " + id );
                }
                else
                {
                    ArtifactVersion minimum = new DefaultArtifactVersion( issue );
                    ArtifactVersion version = new DefaultArtifactVersion( plugin.getVersion() );
                    if ( version.compareTo( minimum ) < 0 )
                    {
                        getLog().error( "plugin with non-reproducible output: " + id + ", require minimum " + issue );
                        fail = true;
                    }
                    else
                    {
                        getLog().info( "no known issue with " + id + " (>= " + issue + ")" );
                    }
                }
            }
        }

        if ( fail )
        {
            getLog().info( "current module pom.xml is " + project.getBasedir() + "/pom.xml" );
            MavenProject parent = project;
            while ( ( parent = parent.getParent() ) != null )
            {
                getLog().info( "        parent pom.xml is " + parent.getBasedir() + "/pom.xml" );
            }
            throw new MojoExecutionException( "plugin with non-reproducible output found with fix available" );
        }
    }

    private Properties loadIssues()
        throws MojoExecutionException
    {
        try ( InputStream in = getClass().getResourceAsStream( "not-reproducible-plugins.properties" ) )
        {
            Properties prop = new Properties();
            prop.load( in );

            Properties result = new Properties();
            for ( Map.Entry<Object, Object> entry : prop.entrySet() )
            {
                String plugin = entry.getKey().toString().replace( '+', ':' );
                if ( !plugin.contains( ":" ) )
                {
                    plugin = "org.apache.maven.plugins:" + plugin;
                }
                result.put( plugin, entry.getValue() );
            }
            return result;
        }
        catch ( IOException ioe )
        {
            throw new MojoExecutionException( "Cannot load issues file", ioe );
        }
    }
}

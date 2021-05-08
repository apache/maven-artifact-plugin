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

import java.io.File;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.PropertyUtils;
import org.apache.maven.toolchain.Toolchain;

/**
 * Buildinfo content writer.
 */
class BuildInfoWriter
{
    private final Log log;
    private final PrintWriter p;
    private final boolean mono;
    private final Map<Artifact, String> artifacts = new LinkedHashMap<>();
    private int projectCount = -1;
    private boolean ignoreJavadoc = true;
    private Set<String> ignore;
    private Toolchain toolchain;

    BuildInfoWriter( Log log, PrintWriter p, boolean mono )
    {
        this.log = log;
        this.p = p;
        this.mono = mono;
    }

    void printHeader( MavenProject project, MavenProject aggregate, boolean reproducible )
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
        if ( reproducible )
        {
            p.println( "# build environment information (simplified for reproducibility)" );
            p.println( "java.version=" + extractJavaMajorVersion( System.getProperty( "java.version" ) ) );
            String ls = System.getProperty( "line.separator" );
            p.println( "os.name=" + ( "\n".equals( ls ) ? "Unix" : "Windows" ) );
        }
        else
        {
            p.println( "# effective build environment information" );
            p.println( "java.version=" + System.getProperty( "java.version" ) );
            p.println( "java.vendor=" + System.getProperty( "java.vendor" ) );
            p.println( "os.name=" + System.getProperty( "os.name" ) );
        }
        p.println();
        p.println( "# Maven rebuild instructions and effective environment" );
        //p.println( "mvn.rebuild-args=" + rebuildArgs );
        if ( !reproducible )
        {
            p.println( "mvn.version=" + MavenVersion.createMavenVersionString() );
        }
        if ( ( project.getPrerequisites() != null ) && ( project.getPrerequisites().getMaven() != null ) )
        {
            // TODO wrong algorithm, should reuse algorithm written in versions-maven-plugin
            p.println( "mvn.minimum.version=" + project.getPrerequisites().getMaven() );
        }
        if ( toolchain != null )
        {
            String javaVersion = JdkToolchainUtil.getJavaVersion( toolchain );
            if ( reproducible )
            {
                javaVersion = extractJavaMajorVersion( javaVersion );
            }
            p.println( "mvn.toolchain.jdk=" + javaVersion );
        }

        if ( !mono && ( aggregate != null ) )
        {
            p.println( "mvn.aggregate.artifact-id=" + aggregate.getArtifactId() );
        }

        p.println();
        p.println( "# " + ( mono ? "" : "aggregated " ) + "output" );
    }

    private static String extractJavaMajorVersion( String javaVersion )
    {
        if ( javaVersion.startsWith( "1." ) )
        {
            javaVersion = javaVersion.substring( 2 );
        }
        return javaVersion.substring( 0, javaVersion.indexOf( '.' ) );
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

    void printArtifacts( MavenProject project )
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
            if ( ignoreJavadoc && "javadoc".equals( attached.getClassifier() ) )
            {
                // TEMPORARY ignore javadoc, waiting for MJAVADOC-627 in m-javadoc-p 3.2.0
                continue;
            }
            if ( isIgnore( attached ) )
            {
                continue;
            }
            printArtifact( prefix, n++, attached );
        }
    }

    private void printArtifact( String prefix, int i, Artifact artifact )
        throws MojoExecutionException
    {
        prefix = prefix + i;
        if ( artifact.getFile().isDirectory() )
        {
            // edge case found in a distribution module with default packaging and skip set for
            // m-jar-p: should use pom packaging instead
            throw new MojoExecutionException( "Artifact " + artifact.getId() + " points to a directory: "
                + artifact.getFile() + ". Packaging should be 'pom'?" );
        }
        printFile( prefix, artifact.getFile(), getArtifactFilename( artifact ) );
        artifacts.put( artifact, prefix );
    }

    private String getArtifactFilename( Artifact artifact )
    {
        StringBuilder path = new StringBuilder( 128 );

        path.append( artifact.getArtifactId() ).append( '-' ).append( artifact.getBaseVersion() );

        if ( artifact.hasClassifier() )
        {
            path.append( '-' ).append( artifact.getClassifier() );
        }

        ArtifactHandler artifactHandler = artifact.getArtifactHandler();

        if ( artifactHandler.getExtension() != null && artifactHandler.getExtension().length() > 0 )
        {
            path.append( '.' ).append( artifactHandler.getExtension() );
        }

        return path.toString();
    }

    void printFile( String prefix, File file )
        throws MojoExecutionException
    {
        printFile( prefix, file, file.getName() );
    }

    private void printFile( String prefix, File file, String filename )
        throws MojoExecutionException
    {
        p.println();
        p.println( prefix + ".filename=" + filename );
        p.println( prefix + ".length=" + file.length() );
        p.println( prefix + ".checksums.sha512=" + DigestHelper.calculateSha512( file ) );
    }

    Map<Artifact, String> getArtifacts()
    {
        return artifacts;
    }

    /**
     * Load buildinfo file and extracts properties on output files.
     *
     * @param buildinfo the build info file
     * @return output properties
     * @throws MojoExecutionException
     */
    static Properties loadOutputProperties( File buildinfo )
        throws MojoExecutionException
    {
        Properties prop = PropertyUtils.loadOptionalProperties( buildinfo );
        for ( String name : prop.stringPropertyNames() )
        {
            if ( !name.startsWith( "outputs." ) || name.endsWith( ".coordinates" ) )
            {
                prop.remove( name );
            }
        }
        return prop;
    }

    boolean getIgnoreJavadoc()
    {
        return ignoreJavadoc;
    }

    void setIgnoreJavadoc( boolean ignoreJavadoc )
    {
        this.ignoreJavadoc = ignoreJavadoc;
    }

    void setIgnore( Set<String> ignore )
    {
        this.ignore = ignore;
    }

    private boolean isIgnore( Artifact attached )
    {
        String classifier = attached.getClassifier();
        String extension = attached.getType();
        String search = ( classifier == null ) ? "" : ( classifier + '.' ) + extension;
        return ignore.contains( search );
    }

    public void setToolchain( Toolchain toolchain )
    {
        this.toolchain = toolchain;
    }
}

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
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.shared.utils.logging.MessageUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Creates a buildinfo file recording build environment and output, as specified in
 * <a href="https://reproducible-builds.org/docs/jvm/">Reproducible Builds for the JVM</a>
 * for mono-module build, and extended for multi-module build.
 * Then if a remote repository is configured, check against reference content in it.
 */
@Mojo( name = "buildinfo", defaultPhase = LifecyclePhase.VERIFY )
public class BuildinfoMojo
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
     * Repository for reference build, containing either reference buildinfo file or reference artifacts.<br/>
     * Format: <code>id</code> or <code>url</code> or <code>id::url</code>
     * <dl>
     * <dt>id</dt>
     * <dd>The id can be used to pick up the correct credentials from the settings.xml</dd>
     * <dt>url</dt>
     * <dd>The location of the repository</dd>
     * </dl>
     */
    @Parameter( property = "reference.repo" )
    private String referenceRepo;

    /**
     * Used for attaching the buildinfo file in the project.
     */
    @Component
    private MavenProjectHelper projectHelper;

    @Component
    private ArtifactFactory artifactFactory;

    /**
     * The entry point to Maven Artifact Resolver, i.e. the component doing all the work.
     */
    @Component
    private RepositorySystem repoSystem;

    /**
     * The current repository/network configuration of Maven.
     */
    @Parameter( defaultValue = "${repositorySystemSession}", readonly = true )
    private RepositorySystemSession repoSession;

    /**
     * The project's remote repositories to use for the resolution.
     */
    @Parameter( defaultValue = "${project.remoteProjectRepositories}", readonly = true )
    private List<RemoteRepository> remoteRepos;

    public void execute()
        throws MojoExecutionException
    {
        boolean mono = reactorProjects.size() == 1;

        if ( !mono )
        {
            // if multi-module build, generate (aggregate) buildinfo only in last module
            MavenProject last = reactorProjects.get( reactorProjects.size() - 1 );
            if  ( project != last )
            {
                getLog().info( "Skipping intermediate buildinfo, aggregate will be " + last.getArtifactId() );
                return;
            }
        }

        // generate buildinfo
        Map<Artifact, String> artifacts = generateBuildinfo( mono );
        getLog().info( "Saved " + ( mono ? "" : "aggregate " ) + "info on build to " + buildinfoFile );

        // eventually attach
        if ( attach )
        {
            projectHelper.attachArtifact( project, "buildinfo", buildinfoFile );
        }
        else
        {
            getLog().info( "NOT adding buildinfo to the list of attached artifacts." );
        }

        // eventually check against reference
        if ( referenceRepo != null )
        {
            getLog().info( "Checking against reference build from " + referenceRepo + "..." );
            checkAgainstReference( mono, artifacts );
        }
    }

    /**
     * Generate buildinfo file.
     *
     * @param mono is it a mono-module build?
     * @return a Map of artifacts added to the build info with their associated property key prefix
     *         (<code>outputs.[#module.].#artifact</code>)
     * @throws MojoExecutionException
     */
    private Map<Artifact, String> generateBuildinfo( boolean mono )
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

            return bi.getArtifacts();
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Error creating file " + buildinfoFile, e );
        }
    }

    /**
     * Check current build result with reference.
     *
     * @param mono is it a mono-module build?
     * @artifacts a Map of artifacts added to the build info with their associated property key prefix
     *            (<code>outputs.[#module.].#artifact</code>)
     * @throws MojoExecutionException
     */
    private void checkAgainstReference( boolean mono, Map<Artifact, String> artifacts )
        throws MojoExecutionException
    {
        MavenProject root = mono ? project : getExecutionRoot();
        File referenceDir = new File( root.getBuild().getDirectory(), "reference" );
        referenceDir.mkdirs();

        // download or create reference buildinfo
        File referenceBuildinfo = downloadOrCreateReferenceBuildinfo( mono, artifacts, referenceDir );

        // compare outputs from reference buildinfo vs actual
        compareWithReference( artifacts, referenceBuildinfo );
    }

    private File downloadOrCreateReferenceBuildinfo( boolean mono, Map<Artifact, String> artifacts, File referenceDir )
        throws MojoExecutionException
    {
        RemoteRepository repo = createReferenceRepo();

        ReferenceBuildinfoUtil rmb = new ReferenceBuildinfoUtil( getLog(), referenceDir, artifacts,
                                                                       artifactFactory, repoSystem, repoSession );

        return rmb.downloadOrCreateReferenceBuildinfo( repo, project, buildinfoFile, mono );
    }

    private void compareWithReference( Map<Artifact, String> artifacts, File referenceBuildinfo )
        throws MojoExecutionException
    {
        Properties actual = BuildInfoWriter.loadOutputProperties( buildinfoFile );
        Properties reference = BuildInfoWriter.loadOutputProperties( referenceBuildinfo );

        int ok = 0;
        File referenceDir = referenceBuildinfo.getParentFile();
        for ( Map.Entry<Artifact, String> entry : artifacts.entrySet() )
        {
            Artifact artifact = entry.getKey();
            String prefix = entry.getValue();

            if ( checkArtifact( artifact, prefix, reference, actual, referenceDir ) )
            {
                ok++;
            }
        }

        int ko = artifacts.size() - ok;
        int missing = reference.size() / 3 /* 3 property keys par file: filename, length and checksums.sha512 */;

        if ( ko + missing > 0 )
        {
            getLog().warn( "Reproducible Build output summary: " + ok + " files ok, " + ko + " different, " + missing
                + " missing" );
            getLog().warn( "diff " + relative( referenceBuildinfo ) + " " + relative( buildinfoFile ) );
        }
        else
        {
            getLog().info( "Reproducible Build output summary: " + ok + " files ok" );
        }
    }

    private boolean checkArtifact( Artifact artifact, String prefix, Properties reference, Properties actual,
                                   File referenceDir )
    {
        String actualFilename = (String) actual.remove( prefix + ".filename" );
        String actualLength = (String) actual.remove( prefix + ".length" );
        String actualSha512 = (String) actual.remove( prefix + ".checksums.sha512" );

        String referencePrefix = findPrefix( reference, actualFilename );
        String referenceLength = (String) reference.remove( referencePrefix + ".length" );
        String referenceSha512 = (String) reference.remove( referencePrefix + ".checksums.sha512" );

        if ( !actualLength.equals( referenceLength ) )
        {
            getLog().warn( "size mismatch " + MessageUtils.buffer().strong( actualFilename )
                + diffoscope( artifact, referenceDir ) );
            return false;
        }
        else if ( !actualSha512.equals( referenceSha512 ) )
        {
            getLog().warn( "sha512 mismatch " + MessageUtils.buffer().strong( actualFilename )
                + diffoscope( artifact, referenceDir ) );
            return false;
        }
        return true;
    }

    private String diffoscope( Artifact a, File referenceDir )
    {
        File actual = a.getFile();
        File reference = new File( referenceDir, actual.getName() );
        return ": diffoscope " + relative( reference ) + " " + relative( actual );
    }

    private String relative( File file )
    {
        return file.getPath().substring( getExecutionRoot().getBasedir().getPath().length() + 1 );
    }

    private String findPrefix( Properties reference, String actualFilename )
    {
        for ( String name : reference.stringPropertyNames() )
        {
            if ( name.endsWith( ".filename" ) && actualFilename.equals( reference.getProperty( name ) ) )
            {
                reference.remove( name );
                return name.substring( 0, name.length() - ".filename".length() );
            }
        }
        return null;
    }

    private RemoteRepository createReferenceRepo()
        throws MojoExecutionException
    {
        if ( referenceRepo.contains( "::" ) )
        {
            // id::url
            int index = referenceRepo.indexOf( "::" );
            String id = referenceRepo.substring( 0, index );
            String url = referenceRepo.substring( index + 2 );
            return createDeploymentArtifactRepository( id, url );
        }
        else if ( referenceRepo.contains( ":" ) )
        {
            // url, will use default "reference" id
            return createDeploymentArtifactRepository( "reference", referenceRepo );
        }

        // id
        for ( RemoteRepository repo : remoteRepos )
        {
            if ( referenceRepo.equals( repo.getId() ) )
            {
                return repo;
            }
        }
        throw new MojoExecutionException( "Could not find repository with id = " + referenceRepo );
    }

    private RemoteRepository createDeploymentArtifactRepository( String id, String url )
    {
        return new RemoteRepository.Builder( id, "default", url ).build();
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

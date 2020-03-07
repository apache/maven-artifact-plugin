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
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.shared.utils.logging.MessageUtils;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.PropertyUtils;
import org.eclipse.aether.AbstractForwardingRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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
     * Directory of the downloaded reference files.
     */
    private File referenceDir;

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
            MavenProject aggregate = reactorProjects.get( reactorProjects.size() - 1 );
            if  ( project != aggregate )
            {
                getLog().info( "Skip intermediate buildinfo, aggregate will be " + aggregate.getArtifactId() );
                return;
            }
        }

        Map<Artifact, String> artifacts = generateBuildinfo( mono );
        getLog().info( "Saved " + ( mono ? "" : "aggregate " ) + "info on build to " + buildinfoFile );

        if ( attach )
        {
            projectHelper.attachArtifact( project, "buildinfo", buildinfoFile );
        }
        else
        {
            getLog().info( "NOT adding buildinfo to the list of attached artifacts." );
        }

        if ( referenceRepo != null )
        {
            getLog().info( "Checking against reference build from " + referenceRepo + "..." );
            checkAgainstReference( mono, artifacts );
        }
    }

    private Map<Artifact, String> generateBuildinfo( boolean mono )
            throws MojoExecutionException
    {
        MavenProject root = mono ? project : getExecutionRoot();

        buildinfoFile.getParentFile().mkdirs();

        referenceDir = new File( root.getBuild().getDirectory(), "reference" );

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

    private void checkAgainstReference( boolean mono, Map<Artifact, String> artifacts )
        throws MojoExecutionException
    {
        RemoteRepository repo = createReferenceRepo();
        referenceDir.mkdirs();

        File referenceBuildinfo = downloadReferenceBuildinfo( repo );

        if ( referenceBuildinfo == null )
        {
            // download reference artifacts
            for ( Artifact artifact : artifacts.keySet() )
            {
                try
                {
                    downloadReference( repo, artifact );
                }
                catch ( ArtifactNotFoundException e )
                {
                    getLog().warn( "Reference artifact not found " + artifact );
                }
            }

            // generate buildinfo from reference artifacts
            referenceBuildinfo = getReference( buildinfoFile );
            try ( PrintWriter p =
                new PrintWriter( new BufferedWriter( new OutputStreamWriter( new FileOutputStream( referenceBuildinfo ),
                                                                             Charsets.ISO_8859_1 ) ) ) )
            {
                BuildInfoWriter bi = new BuildInfoWriter( getLog(), p, mono );

                for ( Map.Entry<Artifact, String> entry : artifacts.entrySet() )
                {
                    Artifact artifact = entry.getKey();
                    String prefix = entry.getValue();
                    bi.printFile( prefix, getReference( artifact.getFile() ) );
                }

                getLog().info( "Minimal buildinfo generated from downloaded artifacts: " + referenceBuildinfo );
            }
            catch ( IOException e )
            {
                throw new MojoExecutionException( "Error creating file " + referenceBuildinfo, e );
            }
        }

        // compare outputs from reference buildinfo vs actual
        Properties actual = loadOutputProperties( buildinfoFile );
        Properties reference = loadOutputProperties( referenceBuildinfo );
        int ok = 0;
        for ( Map.Entry<Artifact, String> entry : artifacts.entrySet() )
        {
            Artifact artifact = entry.getKey();
            String prefix = entry.getValue();

            if ( checkArtifact( artifact, prefix, reference, actual ) )
            {
                ok++;
            }
        }

        int ko = artifacts.size() - ok;
        int missing = reference.size() / 3;
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

    private boolean checkArtifact( Artifact artifact, String prefix, Properties reference, Properties actual )
    {
        String actualFilename = (String) actual.remove( prefix + ".filename" );
        String actualLength = (String) actual.remove( prefix + ".length" );
        String actualSha512 = (String) actual.remove( prefix + ".checksums.sha512" );

        String referencePrefix = findPrefix( reference, actualFilename );
        String referenceLength = (String) reference.remove( referencePrefix + ".length" );
        String referenceSha512 = (String) reference.remove( referencePrefix + ".checksums.sha512" );

        if ( !actualLength.equals( referenceLength ) )
        {
            getLog().warn( "size mismatch " + MessageUtils.buffer().strong( actualFilename ) + diffoscope( artifact ) );
            return false;
        }
        else if ( !actualSha512.equals( referenceSha512 ) )
        {
            getLog().warn( "sha512 mismatch " + MessageUtils.buffer().strong( actualFilename )
                + diffoscope( artifact ) );
            return false;
        }
        return true;
    }

    private String diffoscope( Artifact a )
    {
        File actual = a.getFile();
        File reference = getReference( actual );
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

    private Properties loadOutputProperties( File buildinfo )
        throws MojoExecutionException
    {
        try
        {
            Properties prop = PropertyUtils.loadProperties( buildinfo );
            for ( String name : prop.stringPropertyNames() )
            {
                if ( ! name.startsWith( "outputs." ) || name.endsWith( ".coordinates" ) )
                {
                    prop.remove( name );
                }
            }
            return prop;
        }
        catch ( IOException ioe )
        {
            throw new MojoExecutionException( "Error reading buildinfo file " + buildinfo, ioe );
        }
    }

    private File downloadReferenceBuildinfo( RemoteRepository repo )
        throws MojoExecutionException
    {
        Artifact buildinfo =
                        artifactFactory.createArtifactWithClassifier( project.getGroupId(), project.getArtifactId(),
                                                                      project.getVersion(), "buildinfo", "" );
        try
        {
            File file = downloadReference( repo, buildinfo );

            getLog().info( "Reference buildinfo file found, copied to " + file );

            return file;
        }
        catch ( ArtifactNotFoundException e )
        {
            getLog().warn( "Reference buildinfo file not found: "
                + "it will be generated from downloaded reference artifacts" );
        }

        return null;
    }

    private File downloadReference( RemoteRepository repo, Artifact artifact )
        throws MojoExecutionException, ArtifactNotFoundException
    {
        try
        {
            ArtifactRequest request = new ArtifactRequest();
            request.setArtifact( new DefaultArtifact( artifact.getGroupId(), artifact.getArtifactId(),
                                                      artifact.getClassifier(),
                                                      artifact.getArtifactHandler().getExtension(),
                                                      artifact.getVersion() ) );
            request.setRepositories( Collections.singletonList( repo ) );

            ArtifactResult result =
                repoSystem.resolveArtifact( new NoWorkspaceRepositorySystemSession( repoSession ), request );
            File resultFile = result.getArtifact().getFile();
            File destFile = getReference( resultFile );

            FileUtils.copyFile( resultFile, destFile );

            return destFile;
        }
        catch ( org.eclipse.aether.resolution.ArtifactResolutionException are )
        {
            if ( are.getResult().isMissing() )
            {
                throw new ArtifactNotFoundException( "Artifact not found " + artifact, artifact );
            }
            throw new MojoExecutionException( "Error resolving reference artifact " + artifact, are );
        }
        catch ( IOException ioe )
        {
            throw new MojoExecutionException( "Error copying reference artifact " + artifact, ioe );
        }
    }

    private File getReference( File file )
    {
        return new File( referenceDir, file.getName() );
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

    protected RemoteRepository createDeploymentArtifactRepository( String id, String url )
    {
        return new RemoteRepository.Builder( id, "default", url ).build();
    }

    private static class NoWorkspaceRepositorySystemSession
        extends AbstractForwardingRepositorySystemSession
    {
        private final RepositorySystemSession rss;

        NoWorkspaceRepositorySystemSession( RepositorySystemSession rss )
        {
            this.rss = rss;
        }

        @Override
        protected RepositorySystemSession getSession()
        {
            return rss;
        }

        @Override
        public WorkspaceReader getWorkspaceReader()
        {
            return null;
        }
    }
}

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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.logging.MessageUtils;
import org.apache.maven.shared.utils.PropertyUtils;
import org.apache.maven.shared.utils.StringUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Compare current build output with reference either previously installed or downloaded from a remote repository.
 *
 * @since 3.2.0
 */
@Mojo( name = "compare" )
public class CompareMojo
    extends AbstractBuildinfoMojo
{
    /**
     * Repository for reference build, containing either reference buildinfo file or reference artifacts.<br/>
     * Format: <code>id</code> or <code>url</code> or <code>id::url</code>
     * <dl>
     * <dt>id</dt>
     * <dd>The repository id</dd>
     * <dt>url</dt>
     * <dd>The url of the repository</dd>
     * </dl>
     * @see <a href="https://maven.apache.org/ref/current/maven-model/maven.html#repository">repository definition</a>
     */
    @Parameter( property = "reference.repo", defaultValue = "central" )
    private String referenceRepo;

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

    @Component
    private ArtifactRepositoryLayout artifactRepositoryLayout;

    @Override
    public void execute( Map<Artifact, String> artifacts )
        throws MojoExecutionException
    {
        getLog().info( "Checking against reference build from " + referenceRepo + "..." );
        checkAgainstReference( artifacts );
    }

    /**
     * Check current build result with reference.
     *
     * @artifacts a Map of artifacts added to the build info with their associated property key prefix
     *            (<code>outputs.[#module.].#artifact</code>)
     * @throws MojoExecutionException
     */
    private void checkAgainstReference( Map<Artifact, String> artifacts )
        throws MojoExecutionException
    {
        boolean mono = reactorProjects.size() == 1;
        MavenProject root = mono  ? project : getExecutionRoot();
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
        List<String> okFilenames = new ArrayList<>();
        List<String> koFilenames = new ArrayList<>();
        List<String> diffoscopes = new ArrayList<>();
        File referenceDir = referenceBuildinfo.getParentFile();
        for ( Map.Entry<Artifact, String> entry : artifacts.entrySet() )
        {
            Artifact artifact = entry.getKey();
            String prefix = entry.getValue();

            String diffoscope = checkArtifact( artifact, prefix, reference, actual, referenceDir ); 
            if ( diffoscope == null )
            {
                ok++;
                okFilenames.add( artifact.getFile().getName() );
            }
            else
            {
                koFilenames.add( artifact.getFile().getName() );
                diffoscopes.add( diffoscope );
            }
        }

        int ko = artifacts.size() - ok;
        int missing = reference.size() / 3 /* 3 property keys par file: filename, length and checksums.sha512 */;

        if ( ko + missing > 0 )
        {
            getLog().warn( "Reproducible Build output summary: " + MessageUtils.buffer().success( ok + " files ok" )
                + ", " + MessageUtils.buffer().failure( ko + " different" )
                + ( ( missing == 0 ) ? "" : ( ", " + MessageUtils.buffer().warning( missing + " missing" ) ) ) );
            getLog().warn( "see " + MessageUtils.buffer().project( "diff " + relative( referenceBuildinfo ) + " "
                + relative( buildinfoFile ) ).toString() );
            getLog().warn( "see also https://maven.apache.org/guides/mini/guide-reproducible-builds.html" );
          }
        else
        {
            getLog().info( "Reproducible Build output summary: " + MessageUtils.buffer().success( ok + " files ok" ) );
        }


        // save .compare file
        File compare = new File( buildinfoFile.getParentFile(),
                                 buildinfoFile.getName().replaceFirst( ".buildinfo$", ".compare" ) );
        try ( PrintWriter p =
            new PrintWriter( new BufferedWriter( new OutputStreamWriter( new FileOutputStream( compare ),
                                                                         StandardCharsets.UTF_8 ) ) ) )
        {
            p.println( "version=" + project.getVersion() );
            p.println( "ok=" + ok );
            p.println( "ko=" + ko );
            p.println( "okFiles=\"" + StringUtils.join( okFilenames.iterator(), " " ) + '"' );
            p.println( "koFiles=\"" + StringUtils.join( koFilenames.iterator(), " " ) + '"' );
            Properties ref = PropertyUtils.loadOptionalProperties( referenceBuildinfo );
            String v = ref.getProperty( "java.version" );
            if ( v != null )
            {
                p.println( "reference_java_version=\"" + v + '"' );
            }
            v = ref.getProperty( "os.name" );
            if ( v != null )
            {
                p.println( "reference_os_name=\"" + v + '"' );
            }
            for ( String diffoscope : diffoscopes )
            {
                p.print( "# " );
                p.println( diffoscope );
            }
            getLog().info( "Reproducible Build output comparison saved to " + compare );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Error creating file " + compare, e );
        }

        copyAggregateToRoot( compare );
    }

    private String checkArtifact( Artifact artifact, String prefix, Properties reference, Properties actual,
                                  File referenceDir )
    {
        String actualFilename = (String) actual.remove( prefix + ".filename" );
        String actualLength = (String) actual.remove( prefix + ".length" );
        String actualSha512 = (String) actual.remove( prefix + ".checksums.sha512" );

        String referencePrefix = findPrefix( reference, actualFilename );
        String referenceLength = (String) reference.remove( referencePrefix + ".length" );
        String referenceSha512 = (String) reference.remove( referencePrefix + ".checksums.sha512" );

        String issue = null;
        if ( !actualLength.equals( referenceLength ) )
        {
            issue = "size";
        }
        else if ( !actualSha512.equals( referenceSha512 ) )
        {
            issue = "sha512";
        }

        if ( issue != null )
        {
            String diffoscope = diffoscope( artifact, referenceDir );
            getLog().warn( issue + " mismatch " + MessageUtils.buffer().strong( actualFilename ) + ": investigate with "
                + MessageUtils.buffer().project( diffoscope ) );
            return diffoscope;
        }
        return null;
    }

    private String diffoscope( Artifact a, File referenceDir )
    {
        File actual = a.getFile();
        // notice: actual file name may have been defined in pom
        // reference file name is taken from repository format
        File reference = new File( referenceDir, getRepositoryFilename( a ) );
        return "diffoscope " + relative( reference ) + " " + relative( actual );
    }

    private String getRepositoryFilename( Artifact a )
    {
        String path = artifactRepositoryLayout.pathOf( a );
        return path.substring( path.lastIndexOf( '/' ) );
    }

    private String relative( File file )
    {
        return file.getPath().substring( getExecutionRoot().getBasedir().getPath().length() + 1 );
    }

    private static String findPrefix( Properties reference, String actualFilename )
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

    private static RemoteRepository createDeploymentArtifactRepository( String id, String url )
    {
        return new RemoteRepository.Builder( id, "default", url ).build();
    }
}

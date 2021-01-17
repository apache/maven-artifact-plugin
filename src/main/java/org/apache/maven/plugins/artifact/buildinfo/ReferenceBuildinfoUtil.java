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
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.io.IOUtil;
import org.codehaus.plexus.util.FileUtils;
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
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Utility to download or generate reference buildinfo.
 */
class ReferenceBuildinfoUtil
{
    private static final Set<String> JAR_TYPES;
    
    static
    {
        Set<String> types = new HashSet<>();
        types.add( "jar" );
        types.add( "test-jar" );
        types.add( "war" );
        types.add( "ear" );
        types.add( "rar" );
        types.add( "maven-plugin" );
        JAR_TYPES = Collections.unmodifiableSet( types );
    }

    private final Log log;

    /**
     * Directory of the downloaded reference files.
     */
    private final File referenceDir;

    private final Map<Artifact, String> artifacts;

    private final ArtifactFactory artifactFactory;

    private final RepositorySystem repoSystem;

    private final RepositorySystemSession repoSession;

    ReferenceBuildinfoUtil( Log log, File referenceDir, Map<Artifact, String> artifacts,
                                      ArtifactFactory artifactFactory, RepositorySystem repoSystem,
                                      RepositorySystemSession repoSession )
    {
        this.log = log;
        this.referenceDir = referenceDir;
        this.artifacts = artifacts;
        this.artifactFactory = artifactFactory;
        this.repoSystem = repoSystem;
        this.repoSession = repoSession;
    }

    File downloadOrCreateReferenceBuildinfo( RemoteRepository repo, MavenProject project, File buildinfoFile,
                                                    boolean mono )
        throws MojoExecutionException
    {
        File referenceBuildinfo = downloadReferenceBuildinfo( repo, project );

        if ( referenceBuildinfo == null )
        {
            // download reference artifacts and guess Java version and OS
            String javaVersion = null;
            String osName = null;
            Map<Artifact, File> referenceArtifacts = new HashMap<>();
            for ( Artifact artifact : artifacts.keySet() )
            {
                try
                {
                    // download
                    File file = downloadReference( repo, artifact );
                    referenceArtifacts.put( artifact, file );
                    
                    // guess Java version and OS
                    if ( ( javaVersion == null ) && JAR_TYPES.contains( artifact.getType() ) )
                    {
                        log.debug( "Guessing java.version and os.name from jar " + file );
                        try ( JarFile jar = new JarFile( file ) )
                        {
                            Manifest manifest = jar.getManifest();
                            if ( manifest != null )
                            {
                                javaVersion = extractJavaVersion( manifest );
                                osName = extractOsName( artifact, jar );
                            }
                            else
                            {
                                log.warn( "no MANIFEST.MF found in jar " + file );
                            }
                        }
                        catch ( IOException e )
                        {
                            log.warn( "unable to open jar file " + file, e );
                        }
                    }
                }
                catch ( ArtifactNotFoundException e )
                {
                    log.warn( "Reference artifact not found " + artifact );
                }
            }

            // generate buildinfo from reference artifacts
            referenceBuildinfo = getReference( buildinfoFile );
            try ( PrintWriter p =
                new PrintWriter( new BufferedWriter( new OutputStreamWriter( new FileOutputStream( referenceBuildinfo ),
                                                                             StandardCharsets.UTF_8 ) ) ) )
            {
                BuildInfoWriter bi = new BuildInfoWriter( log, p, mono );

                if ( javaVersion != null || osName != null )
                {
                    p.println( "# effective build environment information" );
                    if ( javaVersion != null )
                    {
                        p.println( "java.version=" + javaVersion );
                        log.info( "Reference build java.version: " + javaVersion );
                    }
                    if ( osName != null )
                    {
                        p.println( "os.name=" + osName );
                        log.info( "Reference build os.name: " + osName );

                        // check against current line separator
                        String expectedLs = osName.startsWith( "Windows" ) ? "\r\n" : "\n";
                        if ( !expectedLs.equals( System.lineSeparator() ) )
                        {
                            log.warn( "Current System.lineSeparator() does not match reference build OS" );

                            String ls = System.getProperty( "line.separator" );
                            if ( !ls.equals( System.lineSeparator() ) )
                            {
                                log.warn( "System.lineSeparator() != System.getProperty( \"line.separator\" ): "
                                    + "too late standard system property update..." );
                            }
                        }
                    }
                }

                for ( Map.Entry<Artifact, String> entry : artifacts.entrySet() )
                {
                    Artifact artifact = entry.getKey();
                    String prefix = entry.getValue();
                    File referenceFile = referenceArtifacts.get( artifact );
                    if ( referenceFile != null )
                    {
                        bi.printFile( prefix, referenceFile );
                    }
                }

                log.info( "Minimal buildinfo generated from downloaded artifacts: " + referenceBuildinfo );
            }
            catch ( IOException e )
            {
                throw new MojoExecutionException( "Error creating file " + referenceBuildinfo, e );
            }
        }

        return referenceBuildinfo;
    }

    private String extractJavaVersion( Manifest manifest )
    {
        Attributes attr = manifest.getMainAttributes();

        String value = attr.getValue( "Build-Jdk-Spec" ); // MSHARED-797
        if ( value != null )
        {
            return value + " (from MANIFEST.MF Build-Jdk-Spec)";
        }

        value = attr.getValue( "Build-Jdk" );
        if ( value != null )
        {
            return String.valueOf( value ) + " (from MANIFEST.MF Build-Jdk)";
        }

        return null;
    }

    private String extractOsName( Artifact a, JarFile jar )
    {
        String entryName = "META-INF/maven/" + a.getGroupId() + '/' + a.getArtifactId() + "/pom.properties";
        try ( InputStream in = jar.getInputStream( jar.getEntry( entryName ) ) )
        {
            String content = IOUtil.toString( in, StandardCharsets.UTF_8.name() );
            log.debug( "Manifest content: " + content );
            if ( content.contains( "\r\n" ) )
            {
                return "Windows (from pom.properties newline)";
            }
            else if ( content.contains( "\n" ) )
            {
                return "Unix (from pom.properties newline)";
            }
        }
        catch ( IOException e )
        {
            log.warn( "Unable to read " + entryName + " from " + jar, e );
        }
        return null;
    }

    private File downloadReferenceBuildinfo( RemoteRepository repo, MavenProject project )
        throws MojoExecutionException
    {
        Artifact buildinfo =
                        artifactFactory.createArtifactWithClassifier( project.getGroupId(), project.getArtifactId(),
                                                                      project.getVersion(), "buildinfo", "" );
        try
        {
            File file = downloadReference( repo, buildinfo );

            log.info( "Reference buildinfo file found, copied to " + file );

            return file;
        }
        catch ( ArtifactNotFoundException e )
        {
            log.warn( "Reference buildinfo file not found: "
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

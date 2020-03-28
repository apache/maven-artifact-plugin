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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
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
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility to download or generate reference buildinfo.
 */
public class ReferenceBuildinfoUtil
{
    private final Log log;

    /**
     * Directory of the downloaded reference files.
     */
    private final File referenceDir;

    private final Map<Artifact, String> artifacts;

    private final ArtifactFactory artifactFactory;

    private final RepositorySystem repoSystem;

    private final RepositorySystemSession repoSession;

    public ReferenceBuildinfoUtil( Log log, File referenceDir, Map<Artifact, String> artifacts,
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

    public File downloadOrCreateReferenceBuildinfo( RemoteRepository repo, MavenProject project, File buildinfoFile,
                                                    boolean mono )
        throws MojoExecutionException
    {
        File referenceBuildinfo = downloadReferenceBuildinfo( repo, project );

        if ( referenceBuildinfo == null )
        {
            // download reference artifacts
            Map<Artifact, File> referenceArtifacts = new HashMap<>();
            for ( Artifact artifact : artifacts.keySet() )
            {
                try
                {
                    referenceArtifacts.put( artifact, downloadReference( repo, artifact ) );
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
                                                                             Charsets.ISO_8859_1 ) ) ) )
            {
                BuildInfoWriter bi = new BuildInfoWriter( log, p, mono );

                for ( Map.Entry<Artifact, String> entry : artifacts.entrySet() )
                {
                    Artifact artifact = entry.getKey();
                    String prefix = entry.getValue();
                    bi.printFile( prefix, referenceArtifacts.get( artifact ) );
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

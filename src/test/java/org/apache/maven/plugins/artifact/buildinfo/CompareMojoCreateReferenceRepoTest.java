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
package org.apache.maven.plugins.artifact.buildinfo;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

/**
 * Tests for {@link CompareMojo#createReferenceRepo()}.
 */
@MojoTest
class CompareMojoCreateReferenceRepoTest {

    @Inject
    private MavenProject project;

    private final List<RemoteRepository> remoteRepos = new ArrayList<>();

    @BeforeEach
    void setUp() {
        lenient().when(project.getRemoteProjectRepositories()).thenReturn(remoteRepos);
    }

    /**
     * Set a single resolvable repo
     * @param repoId ID of the repository
     * @param repoUrl URL of the repository
     */
    private void setResolvableRepo(String repoId, String repoUrl) {
        remoteRepos.clear();
        remoteRepos.add(new RemoteRepository.Builder(repoId, "default", repoUrl).build());
    }

    private void setResolvableMirrorRepo(String mirrorId, String mirrorUrl, String isMirrorForRepoId) {
        remoteRepos.clear();
        RemoteRepository mirrored =
                new RemoteRepository.Builder(isMirrorForRepoId, "default", "https://placeholder.example.nl").build();
        remoteRepos.add(new RemoteRepository.Builder(mirrorId, "default", mirrorUrl)
                .addMirroredRepository(mirrored)
                .build());
    }

    /**
     * Removes all resolvable repositories and mirrors.
     */
    private void clearResolvableRepos() {
        remoteRepos.clear();
    }

    private void assertRemoteRepo(RemoteRepository result, String repoId, String repoUrl) {
        assertNotNull(result);
        assertEquals(repoId, result.getId());
        assertEquals(repoUrl, result.getUrl());
    }

    @Test
    @InjectMojo(goal = "compare")
    void centralRepoNoMirror(CompareMojo mojo) throws Exception {
        // Make the default repo 'central' resolvable
        setResolvableRepo("central", "https://repo.maven.apache.org/maven2");

        RemoteRepository result = mojo.createReferenceRepo();

        assertRemoteRepo(result, "central", "https://repo.maven.apache.org/maven2");
    }

    @Test
    @InjectMojo(goal = "compare")
    void centralRepoWithMirror(CompareMojo mojo) throws Exception {
        // When a mirror is configured, Maven replaces central with the mirror in remoteRepos.
        // The original "central" entry is NOT present, only the mirror is.
        setResolvableMirrorRepo("my-mirror", "https://my-mirror.example.nl/central", "central");

        RemoteRepository result = mojo.createReferenceRepo();

        assertRemoteRepo(result, "my-mirror", "https://my-mirror.example.nl/central");
    }

    @Test
    @InjectMojo(goal = "compare")
    @MojoParameter(name = "referenceRepo", value = "releases")
    void nonCentralRepoNoMirror(CompareMojo mojo) throws Exception {
        setResolvableRepo("releases", "https://repo.example.nl/releases");

        RemoteRepository result = mojo.createReferenceRepo();

        assertRemoteRepo(result, "releases", "https://repo.example.nl/releases");
    }

    @Test
    @InjectMojo(goal = "compare")
    @MojoParameter(name = "referenceRepo", value = "releases")
    void nonCentralRepoWithMirror(CompareMojo mojo) throws Exception {
        // When a mirror is configured for a non-central repo, Maven replaces the original with the mirror.
        // The original "releases" entry is NOT present, only the mirror is.
        setResolvableMirrorRepo("my-mirror", "https://my-mirror.example.nl/releases", "releases");

        RemoteRepository result = mojo.createReferenceRepo();

        assertRemoteRepo(result, "my-mirror", "https://my-mirror.example.nl/releases");
    }

    @Test
    @InjectMojo(goal = "compare")
    @MojoParameter(name = "referenceRepo", value = "nonexistent")
    void repoDoesNotExist(CompareMojo mojo) {
        // When remoteRepos doesn't contain a repo with the requested ID
        clearResolvableRepos();

        MojoExecutionException ex = assertThrows(MojoExecutionException.class, mojo::createReferenceRepo);
        assertTrue(ex.getMessage().contains("Could not find repository with id = nonexistent"));
    }
}

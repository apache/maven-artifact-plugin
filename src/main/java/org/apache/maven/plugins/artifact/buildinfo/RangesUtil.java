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
import javax.inject.Named;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.graph.visitor.PathRecordingDependencyVisitor;
import org.eclipse.aether.version.VersionConstraint;

import static java.util.Optional.ofNullable;

/**
 * Utility class to find dependencies with version ranges in the project.
 */
@Named
public class RangesUtil {

    private final RepositorySystem repoSystem;

    @Inject
    RangesUtil(RepositorySystem repoSystem) {
        this.repoSystem = repoSystem;
    }

    /**
     * Finds all dependencies with version ranges in the project.
     *
     * @param session the Maven session
     * @param project the Maven project to analyze
     * @return a map of dependency nodes with version ranges to their issue descriptions
     * @throws MojoExecutionException if dependency collection fails
     */
    public Map<DependencyNode, String> findVersionRangeDependencies(MavenSession session, MavenProject project)
            throws MojoExecutionException {
        DependencyNode rootNode = collectDependencies(session, project);
        return collectVersionRangeDependencies(rootNode);
    }

    private DependencyNode collectDependencies(MavenSession session, MavenProject project)
            throws MojoExecutionException {
        ArtifactTypeRegistry artifactTypeRegistry =
                session.getRepositorySession().getArtifactTypeRegistry();

        List<Dependency> dependencies = project.getDependencies().stream()
                .map(d -> RepositoryUtils.toDependency(d, artifactTypeRegistry))
                .collect(Collectors.toList());

        List<Dependency> managedDependencies = ofNullable(project.getDependencyManagement())
                .map(DependencyManagement::getDependencies)
                .map(list -> list.stream()
                        .map(d -> RepositoryUtils.toDependency(d, artifactTypeRegistry))
                        .collect(Collectors.toList()))
                .orElse(null);

        CollectRequest collectRequest =
                new CollectRequest(dependencies, managedDependencies, project.getRemoteProjectRepositories());
        collectRequest.setRootArtifact(RepositoryUtils.toArtifact(project.getArtifact()));

        try {
            return repoSystem
                    .collectDependencies(session.getRepositorySession(), collectRequest)
                    .getRoot();
        } catch (DependencyCollectionException e) {
            throw new MojoExecutionException("Cannot build dependency tree: " + e.getMessage(), e);
        }
    }

    private Map<DependencyNode, String> collectVersionRangeDependencies(DependencyNode rootNode) {
        Map<DependencyNode, String> result = new LinkedHashMap<>();

        rootNode.accept(new PathRecordingDependencyVisitor((node, parents) -> {
            VersionConstraint versionConstraint = node.getVersionConstraint();
            if (isVersionRange(versionConstraint)) {
                String path = "";
                if (parents.size() > 1) {
                    path = " via "
                            + parents.stream()
                                    .limit(parents.size() - 1)
                                    .map(n -> n.getDependency().getArtifact().toString())
                                    .collect(Collectors.joining(" --> "));
                }
                result.put(
                        node,
                        "Dependency " + node.getDependency() + path + " has been resolved from a version range "
                                + versionConstraint);
            }

            // track all dependencies
            return false;
        }));

        return result;
    }

    private boolean isVersionRange(VersionConstraint versionConstraint) {
        if (versionConstraint == null) {
            return false;
        }

        if (versionConstraint.getVersion() != null) {
            String version = versionConstraint.getVersion().toString();
            return version.equals("LATEST") || version.equals("RELEASE");
        }

        if (versionConstraint.getRange() != null) {
            return !Objects.equals(
                    versionConstraint.getRange().getLowerBound(),
                    versionConstraint.getRange().getUpperBound());
        }
        return false;
    }
}

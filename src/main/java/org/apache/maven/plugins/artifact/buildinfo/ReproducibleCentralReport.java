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

import java.util.Locale;
import java.util.TreeMap;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;

/**
 * Builds a Reproducible Builds report on the project and its dependencies,
 * using <a href="https://shields.io/badges/reproducible-central-artifact">Reproducible Central Artifact</a> badges.
 * These badges are based on
 * <a href="https://jvm-repo-rebuild.github.io/reproducible-central/">artifact-level data</a> provided by
 * <a href="https://github.com/jvm-repo-rebuild/reproducible-central">Reproducible Central</a>,
 * <a href="https://github.com/jvm-repo-rebuild/reproducible-central/blob/master/bin/update_artifact_badge_data.java">reworked</a>
 * from project-level rebuild results.
 * @since 3.6.0
 */
@Mojo(
        name = "reproducible-central",
        defaultPhase = LifecyclePhase.SITE,
        requiresDependencyResolution = ResolutionScope.RUNTIME,
        requiresProject = true,
        threadSafe = true)
public class ReproducibleCentralReport extends AbstractMavenReport {
    @Component
    private MavenSession session;

    @Override
    protected void executeReport(Locale locale) throws MavenReportException {
        Sink sink = getSink();

        sink.head();
        sink.title();
        sink.text("Reproducible Central Report");
        sink.title_();
        sink.head_();
        sink.body();
        sink.section1();
        sink.sectionTitle1();
        sink.text("Reproducible Central Report");
        sink.sectionTitle1_();
        sink.paragraph();
        sink.text("this project:");
        sink.paragraph_();
        sink.paragraph();
        renderReproducibleCentralArtifact(sink, project);
        sink.paragraph_();

        sink.paragraph();
        sink.text("project's dependencies:");
        sink.paragraph_();

        sink.list();
        new TreeMap<String, Artifact>(project.getArtifactMap()).forEach((key, a) -> {
            sink.listItem();
            renderReproducibleCentralArtifact(sink, a);
            sink.listItem_();
        });
        sink.list_();

        sink.paragraph();
        sink.text("(*) ");
        sink.link("https://reproducible-builds.org/");
        sink.text("Reproducible Builds");
        sink.link_();
        sink.text(" check is done through ");
        sink.link("https://github.com/jvm-repo-rebuild/reproducible-central");
        sink.text("Reproducible Central");
        sink.link_();
        sink.text(" data rendered as ");
        sink.link("https://shields.io/");
        sink.figureGraphics("https://img.shields.io/badge/.-.-green");
        sink.text("Shields.io");
        sink.link_();
        sink.text(" ");
        sink.link("https://shields.io/badges/reproducible-central-artifact");
        sink.text("Reproducible Central Artifact");
        sink.link_();
        sink.text(" badges. If you think data is not accurate, please ");
        sink.link("https://github.com/jvm-repo-rebuild/reproducible-central/blob/master/doc/TOOLS.md");
        sink.text("help us");
        sink.link_();
        sink.text(" improve.");
        sink.paragraph_();
        sink.section1_();
        sink.body_();
    }

    private void renderReproducibleCentralArtifact(Sink sink, MavenProject project) {
        renderReproducibleCentralArtifact(
                sink, project.getGroupId(), project.getArtifactId(), project.getVersion(), null);
    }

    private void renderReproducibleCentralArtifact(Sink sink, Artifact a) {
        renderReproducibleCentralArtifact(sink, a.getGroupId(), a.getArtifactId(), a.getVersion(), a.getScope());
    }

    private void renderReproducibleCentralArtifact(
            Sink sink, String groupId, String artifactId, String version, String scope) {
        String url = "https://jvm-repo-rebuild.github.io/reproducible-central/badge/artifact/"
                + groupId.replace('.', '/') + '/' + artifactId + ".html";
        String badge = "https://img.shields.io/reproducible-central/artifact/" + groupId + '/' + artifactId + '/'
                + version + "?labelColor=1e5b96";
        sink.link(url);
        sink.figureGraphics(badge);
        sink.link_();
        sink.text(' ' + groupId + ':' + artifactId + ':' + version);
        if (scope != null) {
            sink.text(" (" + scope + ")");
        }
    }

    @Override
    public String getOutputName() {
        return "reproducible-central";
    }

    @Override
    public String getName(Locale locale) {
        return "Reproducible Central";
    }

    @Override
    public String getDescription(Locale locale) {
        return "This reports shows if dependencies are proven Reproducible Builds at Reproducible Central.";
    }
}

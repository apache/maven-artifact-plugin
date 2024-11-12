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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.logging.MessageUtils;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

/**
 * Describe build output (experimental).
 * It is expected to be used aggregator used from CLI, ie run at root after everything has run, but not bound to any build
 * phase, where it would be run at root before modules.
 * @since 3.5.2
 */
@Mojo(name = "describe-build-output", aggregator = true, threadSafe = true)
public class DescribeBuildOutputMojo extends AbstractBuildinfoMojo {

    @Override
    public void execute() throws MojoExecutionException {
        // super.execute(); // do not generate buildinfo, just reuse logic from abstract class
        Instant timestamp =
                MavenArchiver.parseBuildOutputTimestamp(outputTimestamp).orElse(null);
        String effective = ((timestamp == null) ? "disabled" : DateTimeFormatter.ISO_INSTANT.format(timestamp));

        diagnose(outputTimestamp, getLog(), project, session.getProjects(), effective);
        getLog().info("");
        initModuleBuildInfo();
        describeBuildOutput();
    }

    private Path rootPath;
    private BuildInfoWriter bi;

    private void describeBuildOutput() throws MojoExecutionException {
        rootPath = getExecutionRoot().getBasedir().toPath();
        bi = newBuildInfoWriter(null, false);

        Map<String, Long> groupIds = session.getProjects().stream()
                .collect(Collectors.groupingBy(MavenProject::getGroupId, Collectors.counting()));
        groupIds.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .forEach(e -> getLog().info("groupId: " + e.getKey() + " (" + e.getValue() + " artifactId"
                        + ((e.getValue() > 1) ? "s" : "") + ")"));

        Map<String, Set<String>> artifactIds = session.getProjects().stream()
                .collect(Collectors.groupingBy(
                        MavenProject::getArtifactId, Collectors.mapping(MavenProject::getGroupId, Collectors.toSet())));
        artifactIds.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(e -> e.getValue().size() > 1)
                .forEach(e ->
                        getLog().info("artifactId: " + e.getKey() + " defined for multiple groupIds: " + e.getValue()));

        getLog().info("");
        getLog().info(MessageUtils.buffer()
                .a("skip/ignore? artifactId")
                .strong("[:classifier][:extension]")
                .a(" = build-path repository-filename size [sha256]")
                .build());

        for (MavenProject p : session.getProjects()) {
            boolean skipped = isSkip(p);
            String s = skipped ? "not-deployed " : "             ";

            // project = pom
            // detect Maven 4 consumer POM transient attachment
            Artifact consumerPom = RepositoryUtils.toArtifacts(p.getAttachedArtifacts()).stream()
                    .filter(a -> "pom".equals(a.getExtension()) && "consumer".equals(a.getClassifier()))
                    .findAny()
                    .orElse(null);

            Artifact pomArtifact = new DefaultArtifact(p.getGroupId(), p.getArtifactId(), null, "pom", p.getVersion());
            if (consumerPom != null) {
                // Maven 4 transient consumer POM attachment is published as the POM, overrides build POM, see
                // https://github.com/apache/maven/blob/c79a7a02721f0f9fd7e202e99f60b593461ba8cc/maven-core/src/main/java/org/apache/maven/internal/transformation/ConsumerPomArtifactTransformer.java#L130-L155
                try {
                    Path pomFile = Files.createTempFile(Paths.get(p.getBuild().getDirectory()), "consumer-", ".pom");
                    Files.copy(consumerPom.getFile().toPath(), pomFile, StandardCopyOption.REPLACE_EXISTING);
                    pomArtifact = pomArtifact.setFile(pomFile.toFile());
                    getLog().info(s + describeArtifact(pomArtifact)); // consumer pom
                    // build pom
                    pomArtifact =
                            new DefaultArtifact(p.getGroupId(), p.getArtifactId(), "build", "pom", p.getVersion());
                } catch (IOException e) {
                    throw new MojoExecutionException("Error processing consumer POM", e);
                }
            }
            pomArtifact = pomArtifact.setFile(p.getFile());
            getLog().info(s + describeArtifact(pomArtifact, skipped));

            // main artifact (when available: pom packaging does not provide main artifact)
            if (p.getArtifact().getFile() != null) {
                getLog().info(s + describeArtifact(RepositoryUtils.toArtifact(p.getArtifact()), skipped));
            }

            // attached artifacts (when available)
            for (Artifact a : RepositoryUtils.toArtifacts(p.getAttachedArtifacts())) {
                if ("pom".equals(a.getExtension()) && "consumer".equals(a.getClassifier())) {
                    // ignore transient consumer POM attachment
                    continue;
                }
                boolean ignored = skipped ? false : isIgnore(a);
                String i = skipped ? s : (ignored ? "RB-ignored   " : "             ");
                getLog().info(i + describeArtifact(a, skipped || ignored));
            }
        }
    }

    private boolean isIgnore(Artifact a) {
        // because this is an aggregator mojo it only runs in the aggregator project so it's the only
        // one that will have `ModuleBuildInfo` available (i.e. none of the other session.projects
        // will have it).
        if (a.getExtension().endsWith(".asc")) {
            return true;
        }
        if (bi.getIgnoreJavadoc(project) && "javadoc".equals(a.getClassifier())) {
            return true;
        }
        return bi.isIgnore(project, a);
    }

    private String describeArtifact(Artifact a) throws MojoExecutionException {
        return describeArtifact(a, false);
    }

    private String describeArtifact(Artifact a, boolean skipped) throws MojoExecutionException {
        String sha256 = skipped ? "" : (" " + sha256(a.getFile()));
        String ce = ("".equals(a.getClassifier()) ? "" : (':' + a.getClassifier()))
                + ("jar".equals(a.getExtension()) ? "" : (":" + a.getExtension()));
        String path = rootPath.relativize(a.getFile().toPath()).toString();
        int i = path.indexOf("target/");
        if (i >= 0) {
            path = MessageUtils.buffer().mojo(path.substring(0, i + 7)).build() + path.substring(i + 7);
        }
        String remoteFilename = BuildInfoWriter.getArtifactFilename(a);
        return /*a.getGroupId() + ':' +*/ a.getArtifactId() /*+ ':' + a.getVersion()*/
                + MessageUtils.buffer().strong(ce) + " = "
                + path + " "
                + (path.endsWith(remoteFilename)
                        ? "-"
                        : MessageUtils.buffer().strong(remoteFilename).build())
                + " " + a.getFile().length() + sha256;
    }

    private String sha256(File file) throws MojoExecutionException {
        try (InputStream is = Files.newInputStream(file.toPath())) {
            return DigestUtils.sha256Hex(is);
        } catch (IOException ioe) {
            throw new MojoExecutionException("cannot read " + file, ioe);
        }
    }

    @Override
    public void execute(Map<org.eclipse.aether.artifact.Artifact, String> artifacts) throws MojoExecutionException {
        // buildinfo generation skipped, method not called
    }
}

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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.stream.Stream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers issue #245: the temporary copy {@link BuildInfoWriter#printArtifacts(MavenProject)} makes of a Maven 4
 * transient consumer POM must be cleaned up once read, and a copy failure must be reported rather than silently
 * leaving a POM artifact pointed at a file that was never written.
 */
class BuildInfoWriterConsumerPomTest {

    @Test
    void consumerPomTempFileIsDeletedAfterUse(@TempDir Path tempDir) throws Exception {
        MavenProject project = newProject(tempDir);
        Path sourcePom = tempDir.resolve("source.pom");
        writeFile(sourcePom, "<project/>");
        attachConsumerPom(project, sourcePom);

        newWriter().printArtifacts(project);

        try (Stream<Path> files = Files.list(tempDir)) {
            assertFalse(
                    files.anyMatch(f -> f.getFileName().toString().startsWith("consumer-")),
                    "the temporary consumer POM copy must be deleted once it has been read");
        }
    }

    @Test
    void consumerPomCopyFailureIsReportedInsteadOfMasked(@TempDir Path tempDir) {
        MavenProject project = newProject(tempDir);
        // points at a source file that does not exist, so Files.copy fails with an IOException
        attachConsumerPom(project, tempDir.resolve("does-not-exist.pom"));

        MojoExecutionException exception =
                assertThrows(MojoExecutionException.class, () -> newWriter().printArtifacts(project));
        assertTrue(exception.getMessage().contains("consumer POM"), exception.getMessage());
    }

    private static MavenProject newProject(Path tempDir) {
        MavenProject project = new MavenProject();
        project.setGroupId("org.apache.maven.plugins.it");
        project.setArtifactId("consumer-pom-it");
        project.setVersion("1.0");
        Build build = new Build();
        build.setDirectory(tempDir.toString());
        project.setBuild(build);
        File pom = tempDir.resolve("pom.xml").toFile();
        writeFile(pom.toPath(), "<project/>");
        project.setFile(pom);
        return project;
    }

    @SuppressWarnings("deprecation") // MavenProject#attachArtifact requires an ArtifactHandlerManager we don't have
    private static void attachConsumerPom(MavenProject project, Path sourceFile) {
        Artifact consumerPom = new DefaultArtifact(
                project.getGroupId(),
                project.getArtifactId(),
                project.getVersion(),
                "runtime",
                "pom",
                "consumer",
                new DefaultArtifactHandler("pom"));
        consumerPom.setFile(sourceFile.toFile());
        project.addAttachedArtifact(consumerPom);
    }

    private static void writeFile(Path file, String content) {
        try {
            Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static BuildInfoWriter newWriter() {
        BuildInfoWriter writer =
                new BuildInfoWriter(new SystemStreamLog(), new PrintWriter(new StringWriter()), true, null);
        writer.setIgnore(Collections.emptyList());
        return writer;
    }
}

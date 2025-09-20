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
import java.io.PrintWriter;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.rtinfo.RuntimeInformation;
import org.apache.maven.toolchain.Toolchain;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;

/**
 * Buildinfo content writer.
 */
class BuildInfoWriter {
    private final Log log;
    private final PrintWriter p;
    private final boolean mono;
    private final RuntimeInformation rtInformation;
    private final Map<Artifact, String> artifacts = new LinkedHashMap<>();
    private int projectCount = -1;
    private boolean ignoreJavadoc = true;
    private List<PathMatcher> ignore;
    private Toolchain toolchain;

    BuildInfoWriter(Log log, PrintWriter p, boolean mono, RuntimeInformation rtInformation) {
        this.log = log;
        this.p = p;
        this.mono = mono;
        this.rtInformation = rtInformation;
    }

    void printHeader(MavenProject project, MavenProject aggregate, boolean reproducible) {
        p.println("# https://reproducible-builds.org/docs/jvm/");
        p.println("buildinfo.version=1.0-SNAPSHOT");
        p.println();
        p.println("name=" + project.getName());
        p.println("group-id=" + project.getGroupId());
        p.println("artifact-id=" + project.getArtifactId());
        p.println("version=" + project.getVersion());
        p.println();
        printSourceInformation(project);
        p.println();
        p.println("# build instructions");
        p.println("build-tool=mvn");
        // p.println( "# optional build setup url, as plugin parameter" );
        p.println();
        if (reproducible) {
            p.println("# build environment information (simplified for reproducibility)");
            p.println("java.version=" + extractJavaMajorVersion(System.getProperty("java.version")));
            String ls = System.lineSeparator();
            p.println("os.name=" + ("\n".equals(ls) ? "Unix" : "Windows"));
        } else {
            p.println("# effective build environment information");
            p.println("java.version=" + System.getProperty("java.version"));
            p.println("java.vendor=" + System.getProperty("java.vendor"));
            p.println("os.name=" + System.getProperty("os.name"));
            p.println("os.version=" + System.getProperty("os.version"));
            p.println("os.arch=" + System.getProperty("os.arch"));
            p.println("line.separator="
                    + System.lineSeparator().replace("\r", "\\r").replace("\n", "\\n"));
        }
        p.println();
        p.println("# Maven rebuild instructions and effective environment");
        if (!reproducible) {
            p.println("mvn.version=" + rtInformation.getMavenVersion());
        }
        if ((project.getPrerequisites() != null) && (project.getPrerequisites().getMaven() != null)) {
            // TODO wrong algorithm, should reuse algorithm written in versions-maven-plugin
            p.println("mvn.minimum.version=" + project.getPrerequisites().getMaven());
        }
        if (toolchain != null) {
            String javaVersion = JdkToolchainUtil.getJavaVersion(toolchain);
            if (reproducible) {
                javaVersion = extractJavaMajorVersion(javaVersion);
            }
            p.println("mvn.toolchain.jdk=" + javaVersion);
        }

        if (!mono && (aggregate != null)) {
            p.println("mvn.aggregate.artifact-id=" + aggregate.getArtifactId());
        }

        p.println();
        p.println("# " + (mono ? "" : "aggregated ") + "output");
    }

    private static String extractJavaMajorVersion(String javaVersion) {
        if (javaVersion.startsWith("1.")) {
            javaVersion = javaVersion.substring(2);
        }
        int index = javaVersion.indexOf('.'); // for example 8.0_202
        if (index < 0) {
            index = javaVersion.indexOf('-'); // for example 17-ea
        }
        return (index < 0) ? javaVersion : javaVersion.substring(0, index);
    }

    private void printSourceInformation(MavenProject project) {
        boolean sourceAvailable = false;
        p.println("# source information");
        // p.println( "# TBD source.* artifact, url should be parameters" );
        if (project.getScm() != null) {
            sourceAvailable = true;
            p.println("source.scm.uri=" + project.getScm().getConnection());
            p.println("source.scm.tag=" + project.getScm().getTag());
            if (project.getArtifact().isSnapshot()) {
                log.warn("SCM source tag in buildinfo source.scm.tag="
                        + project.getScm().getTag() + " does not permit rebuilders reproducible source checkout");
                // TODO is it possible to use Scm API to get SCM version?
            }
        } else {
            p.println("# no scm configured in pom.xml");
        }

        if (!sourceAvailable) {
            log.warn("No source information available in buildinfo for rebuilders...");
        }
    }

    void printArtifacts(MavenProject project) throws MojoExecutionException {
        String prefix = "outputs.";
        if (!mono) {
            // aggregated buildinfo output
            projectCount++;
            prefix += projectCount + ".";
            p.println();
            p.println(prefix + "coordinates=" + project.getGroupId() + ':' + project.getArtifactId());
        }

        // detect Maven 4 consumer POM transient attachment
        Artifact consumerPom = RepositoryUtils.toArtifacts(project.getAttachedArtifacts()).stream()
                .filter(a -> "pom".equals(a.getExtension()) && "consumer".equals(a.getClassifier()))
                .findAny()
                .orElse(null);

        int n = 0;
        Artifact pomArtifact =
                new DefaultArtifact(project.getGroupId(), project.getArtifactId(), null, "pom", project.getVersion());
        if (consumerPom != null) {
            // Maven 4 transient consumer POM attachment is published as the POM, overrides build POM, see
            // https://github.com/apache/maven/blob/c79a7a02721f0f9fd7e202e99f60b593461ba8cc/maven-core/src/main/java/org/apache/maven/internal/transformation/ConsumerPomArtifactTransformer.java#L130-L155
            try {
                Path pomFile = Files.createTempFile(Paths.get(project.getBuild().getDirectory()), "consumer-", ".pom");
                Files.copy(consumerPom.getFile().toPath(), pomFile, StandardCopyOption.REPLACE_EXISTING);
                pomArtifact = pomArtifact.setFile(pomFile.toFile());
            } catch (IOException e) {
                p.println("Error processing consumer POM: " + e);
            }
        } else {
            pomArtifact = pomArtifact.setFile(project.getFile());
        }

        artifacts.put(pomArtifact, prefix + n);
        if (isIgnore(pomArtifact)) {
            p.println("# ignored " + getArtifactFilename(pomArtifact));
        } else {
            printFile(
                    prefix + n++,
                    pomArtifact.getGroupId(),
                    pomArtifact.getFile(),
                    project.getArtifactId() + '-' + project.getVersion() + ".pom");
        }

        if (consumerPom != null) {
            // build pom
            Artifact buildPomArtifact = new DefaultArtifact(
                    project.getGroupId(), project.getArtifactId(), "build", "pom", project.getVersion());
            buildPomArtifact = buildPomArtifact.setFile(project.getFile());

            if (isIgnore(buildPomArtifact)) {
                p.println("# ignored " + getArtifactFilename(buildPomArtifact));
            } else {
                artifacts.put(buildPomArtifact, prefix + n);
                printFile(
                        prefix + n++,
                        buildPomArtifact.getGroupId(),
                        buildPomArtifact.getFile(),
                        project.getArtifactId() + '-' + project.getVersion() + "-build.pom");
            }
        }

        if (project.getArtifact() == null) {
            return;
        }

        if (project.getArtifact().getFile() != null) {
            Artifact main = RepositoryUtils.toArtifact(project.getArtifact());
            if (isIgnore(main)) {
                p.println("# ignored " + getArtifactFilename(main));
            } else {
                printArtifact(prefix, n++, RepositoryUtils.toArtifact(project.getArtifact()));
            }
        }

        for (Artifact attached : RepositoryUtils.toArtifacts(project.getAttachedArtifacts())) {
            if ("pom".equals(attached.getExtension()) && "consumer".equals(attached.getClassifier())) {
                // ignore consumer pom
                continue;
            }
            if (attached.getExtension().endsWith(".asc")) {
                // ignore pgp signatures
                continue;
            }
            if (ignoreJavadoc && "javadoc".equals(attached.getClassifier())) {
                // TEMPORARY ignore javadoc, waiting for MJAVADOC-627 in m-javadoc-p 3.2.0
                continue;
            }
            if (isIgnore(attached)) {
                p.println("# ignored " + getArtifactFilename(attached));
                artifacts.put(attached, null);
                continue;
            }
            printArtifact(prefix, n++, attached);
        }
    }

    private void printArtifact(String prefix, int i, Artifact artifact) throws MojoExecutionException {
        prefix = prefix + i;
        File artifactFile = artifact.getFile();
        if (artifactFile.isDirectory()) {
            if ("pom".equals(artifact.getExtension())) {
                // ignore .pom files: they should not be treated as Artifacts
                return;
            }
            // edge case found in a distribution module with default packaging and skip set for
            // m-jar-p: should use pom packaging instead
            throw new MojoExecutionException("Artifact " + ArtifactIdUtils.toId(artifact) + " points to a directory: "
                    + artifactFile + ". Packaging should be 'pom'?");
        }
        if (!artifactFile.isFile()) {
            log.warn("Ignoring artifact " + ArtifactIdUtils.toId(artifact) + " because it points to inexistent "
                    + artifactFile);
            return;
        }

        printFile(prefix, artifact.getGroupId(), artifact.getFile(), getArtifactFilename(artifact));
        artifacts.put(artifact, prefix);
    }

    static String getArtifactFilename(Artifact artifact) {
        StringBuilder path = new StringBuilder(128);

        path.append(artifact.getArtifactId()).append('-').append(artifact.getBaseVersion());

        if (!artifact.getClassifier().isEmpty()) {
            path.append('-').append(artifact.getClassifier());
        }

        if (!artifact.getExtension().isEmpty()) {
            path.append('.').append(artifact.getExtension());
        }

        return path.toString();
    }

    void printFile(String prefix, String groupId, File file) throws MojoExecutionException {
        printFile(prefix, groupId, file, file.getName());
    }

    private void printFile(String prefix, String groupId, File file, String filename) throws MojoExecutionException {
        p.println();
        p.println(prefix + ".groupId=" + groupId);
        p.println(prefix + ".filename=" + filename);
        p.println(prefix + ".length=" + file.length());
        try (InputStream is = Files.newInputStream(file.toPath())) {
            p.println(prefix + ".checksums.sha512=" + DigestUtils.sha512Hex(is));
        } catch (IOException ioe) {
            throw new MojoExecutionException("Error processing file " + file, ioe);
        } catch (IllegalArgumentException iae) {
            throw new MojoExecutionException("Could not get hash algorithm", iae.getCause());
        }
    }

    Map<Artifact, String> getArtifacts() {
        return artifacts;
    }

    /**
     * Load buildinfo file and extracts properties on output files.
     *
     * @param buildinfo the build info file
     * @return output properties
     * @throws MojoExecutionException
     */
    static Properties loadOutputProperties(File buildinfo) throws MojoExecutionException {
        Properties prop = new Properties();
        if (buildinfo != null) {
            try (InputStream is = Files.newInputStream(buildinfo.toPath())) {
                prop.load(is);
            } catch (IOException e) {
                // silent
            }
        }
        for (String name : prop.stringPropertyNames()) {
            if (!name.startsWith("outputs.") || name.endsWith(".coordinates")) {
                prop.remove(name);
            }
        }
        return prop;
    }

    boolean getIgnoreJavadoc() {
        return ignoreJavadoc;
    }

    void setIgnoreJavadoc(boolean ignoreJavadoc) {
        this.ignoreJavadoc = ignoreJavadoc;
    }

    void setIgnore(List<String> ignore) {
        FileSystem fs = FileSystems.getDefault();
        this.ignore = ignore.stream().map(i -> fs.getPathMatcher("glob:" + i)).collect(Collectors.toList());
    }

    boolean isIgnore(Artifact attached) {
        Path path = Paths.get(attached.getGroupId() + '/' + getArtifactFilename(attached));
        return ignore.stream().anyMatch(m -> m.matches(path));
    }

    public void setToolchain(Toolchain toolchain) {
        this.toolchain = toolchain;
    }
}

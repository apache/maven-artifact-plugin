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

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.rtinfo.RuntimeInformation;
import org.eclipse.aether.AbstractForwardingRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * Utility to download reference artifacts and download or generate reference buildinfo.
 */
class ReferenceBuildinfoUtil {
    private static final Set<String> JAR_EXTENSIONS;

    static {
        Set<String> types = new HashSet<>();
        types.add("jar");
        types.add("war");
        types.add("ear");
        types.add("rar");
        JAR_EXTENSIONS = Collections.unmodifiableSet(types);
    }

    private final Log log;

    /**
     * Directory of the downloaded reference files.
     */
    private final File referenceDir;

    private final Map<Artifact, String> artifacts;

    private final RepositorySystem repoSystem;

    private final RepositorySystemSession repoSession;

    private final RuntimeInformation rtInformation;

    ReferenceBuildinfoUtil(
            Log log,
            File referenceDir,
            Map<Artifact, String> artifacts,
            RepositorySystem repoSystem,
            RepositorySystemSession repoSession,
            RuntimeInformation rtInformation) {
        this.log = log;
        this.referenceDir = referenceDir;
        this.artifacts = artifacts;
        this.repoSystem = repoSystem;
        this.repoSession = repoSession;
        this.rtInformation = rtInformation;
    }

    File downloadOrCreateReferenceBuildinfo(
            RemoteRepository repo, MavenProject project, File buildinfoFile, boolean mono)
            throws MojoExecutionException {
        File referenceBuildinfo = downloadReferenceBuildinfo(repo, project);

        if (referenceBuildinfo != null) {
            log.warn("dropping downloaded reference buildinfo because it may be generated"
                    + " from different maven-artifact-plugin release...");
            // TODO keep a save?
            referenceBuildinfo = null;
        }

        // download reference artifacts and guess Java version and OS
        String javaVersion = null;
        String osName = null;
        String currentJavaVersion = null;
        String currentOsName = null;
        Map<Artifact, File> referenceArtifacts = new HashMap<>();
        for (Artifact artifact : artifacts.keySet()) {
            try {
                // download
                File file = downloadReference(repo, artifact);
                referenceArtifacts.put(artifact, file);

                // guess Java version and OS
                if ((javaVersion == null) && JAR_EXTENSIONS.contains(artifact.getExtension())) {
                    ReproducibleEnv env = extractEnv(file, artifact);
                    if ((env != null) && (env.javaVersion != null)) {
                        javaVersion = env.javaVersion;
                        osName = env.osName;

                        ReproducibleEnv currentEnv = extractEnv(artifact.getFile(), artifact);
                        currentJavaVersion = currentEnv.javaVersion;
                        currentOsName = currentEnv.osName;
                    }
                }
            } catch (ArtifactResolutionException e) {
                log.warn("Reference artifact not found " + artifact);
            }
        }

        try {
            // generate buildinfo from reference artifacts
            referenceBuildinfo = getReference(null, buildinfoFile);
            try (PrintWriter p = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
                    Files.newOutputStream(referenceBuildinfo.toPath()), StandardCharsets.UTF_8)))) {
                BuildInfoWriter bi = new BuildInfoWriter(log, p, mono, rtInformation);

                if (javaVersion != null || osName != null) {
                    p.println("# effective build environment information");
                    if (javaVersion != null) {
                        p.println("java.version=" + javaVersion);
                        log.info("Reference build java.version: " + javaVersion);
                        if (!javaVersion.equals(currentJavaVersion)) {
                            log.error("Current build java.version: " + currentJavaVersion);
                        }
                    }
                    if (osName != null) {
                        p.println("os.name=" + osName);
                        log.info("Reference build os.name: " + osName);

                        // check against current line separator
                        if (!osName.equals(currentOsName)) {
                            log.error("Current build os.name: " + currentOsName);
                        }
                        String expectedLs = osName.startsWith("Windows") ? "\r\n" : "\n";
                        if (!expectedLs.equals(System.lineSeparator())) {
                            log.warn("Current System.lineSeparator() does not match reference build OS");

                            String ls = System.getProperty("line.separator");
                            if (!ls.equals(System.lineSeparator())) {
                                log.warn("System.lineSeparator() != System.getProperty( \"line.separator\" ): "
                                        + "too late standard system property update...");
                            }
                        }
                    }
                }

                for (Map.Entry<Artifact, String> entry : artifacts.entrySet()) {
                    Artifact artifact = entry.getKey();
                    String prefix = entry.getValue();
                    File referenceFile = referenceArtifacts.get(artifact);
                    if (referenceFile != null) {
                        bi.printFile(prefix, artifact.getGroupId(), referenceFile);
                    }
                }

                if (p.checkError()) {
                    throw new MojoExecutionException("Write error to " + referenceBuildinfo);
                }

                log.info("Minimal buildinfo generated from downloaded artifacts: " + referenceBuildinfo);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Error creating file " + referenceBuildinfo, e);
        }

        return referenceBuildinfo;
    }

    private ReproducibleEnv extractEnv(File file, Artifact artifact) {
        log.debug("Guessing java.version and os.name from jar " + file);
        try (JarFile jar = new JarFile(file)) {
            Manifest manifest = jar.getManifest();
            if (manifest != null) {
                String javaVersion = extractJavaVersion(manifest);
                String osName = extractOsName(artifact, jar);
                return new ReproducibleEnv(javaVersion, osName);
            } else {
                log.warn("no MANIFEST.MF found in jar " + file);
            }
        } catch (IOException e) {
            log.warn("unable to open jar file " + file, e);
        }
        return null;
    }

    private String extractJavaVersion(Manifest manifest) {
        Attributes attr = manifest.getMainAttributes();

        String value = attr.getValue("Build-Jdk-Spec"); // MSHARED-797
        if (value != null) {
            return value + " (from MANIFEST.MF Build-Jdk-Spec)";
        }

        value = attr.getValue("Build-Jdk");
        if (value != null) {
            return String.valueOf(value) + " (from MANIFEST.MF Build-Jdk)";
        }

        return null;
    }

    private String extractOsName(Artifact a, JarFile jar) {
        String entryName = "META-INF/maven/" + a.getGroupId() + '/' + a.getArtifactId() + "/pom.properties";
        ZipEntry zipEntry = jar.getEntry(entryName);
        if (zipEntry == null) {
            return null;
        }
        try (InputStream in = jar.getInputStream(zipEntry)) {
            String content = IOUtils.toString(in, StandardCharsets.UTF_8);
            log.debug("Manifest content: " + content);
            if (content.contains("\r\n")) {
                return "Windows (from pom.properties newline)";
            } else if (content.contains("\n")) {
                return "Unix (from pom.properties newline)";
            }
        } catch (IOException e) {
            log.warn("Unable to read " + entryName + " from " + jar, e);
        }
        return null;
    }

    private File downloadReferenceBuildinfo(RemoteRepository repo, MavenProject project) throws MojoExecutionException {
        Artifact buildinfo = new DefaultArtifact(
                project.getGroupId(), project.getArtifactId(), null, "buildinfo", project.getVersion());
        try {
            File file = downloadReference(repo, buildinfo);

            log.info("Reference buildinfo file found, copied to " + file);

            return file;
        } catch (ArtifactResolutionException e) {
            log.info("Reference buildinfo file not found: "
                    + "it will be generated from downloaded reference artifacts");
        }

        return null;
    }

    private File downloadReference(RemoteRepository repo, Artifact artifact)
            throws MojoExecutionException, ArtifactResolutionException {
        try {
            ArtifactRequest request = new ArtifactRequest();
            request.setArtifact(artifact);
            request.setRepositories(Collections.singletonList(repo));

            ArtifactResult result =
                    repoSystem.resolveArtifact(new NoWorkspaceRepositorySystemSession(repoSession), request);
            File resultFile = result.getArtifact().getFile();
            File destFile = getReference(artifact.getGroupId(), resultFile);

            Files.copy(
                    resultFile.toPath(),
                    destFile.toPath(),
                    LinkOption.NOFOLLOW_LINKS,
                    StandardCopyOption.REPLACE_EXISTING);

            return destFile;
        } catch (ArtifactResolutionException are) {
            if (are.getResult().isMissing()) {
                throw are;
            }
            throw new MojoExecutionException("Error resolving reference artifact " + artifact, are);
        } catch (IOException ioe) {
            throw new MojoExecutionException("Error copying reference artifact " + artifact, ioe);
        }
    }

    private File getReference(String groupId, File file) throws IOException {
        File dir;
        if (groupId == null) {
            dir = referenceDir;
        } else {
            dir = new File(referenceDir, groupId);
            if (!dir.isDirectory()) {
                Files.createDirectories(dir.toPath());
            }
        }
        return new File(dir, file.getName());
    }

    private static class NoWorkspaceRepositorySystemSession extends AbstractForwardingRepositorySystemSession {
        private final RepositorySystemSession rss;

        NoWorkspaceRepositorySystemSession(RepositorySystemSession rss) {
            this.rss = rss;
        }

        @Override
        protected RepositorySystemSession getSession() {
            return rss;
        }

        @Override
        public WorkspaceReader getWorkspaceReader() {
            return null;
        }
    }

    private static class ReproducibleEnv {
        @SuppressWarnings("checkstyle:visibilitymodifier")
        public final String javaVersion;

        @SuppressWarnings("checkstyle:visibilitymodifier")
        public final String osName;

        ReproducibleEnv(String javaVersion, String osName) {
            this.javaVersion = javaVersion;
            this.osName = osName;
        }
    }
}

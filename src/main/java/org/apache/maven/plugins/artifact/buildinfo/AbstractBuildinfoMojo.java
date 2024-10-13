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
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.rtinfo.RuntimeInformation;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.eclipse.aether.artifact.Artifact;

/**
 * Base buildinfo-generating class, for goals related to Reproducible Builds {@code .buildinfo} files.
 *
 * @since 3.2.0
 */
public abstract class AbstractBuildinfoMojo extends AbstractMojo {
    /**
     * The Maven project.
     */
    @Component
    protected MavenProject project;

    /**
     * Location of the generated buildinfo file.
     */
    @Parameter(
            defaultValue = "${project.build.directory}/${project.artifactId}-${project.version}.buildinfo",
            required = true,
            readonly = true)
    protected File buildinfoFile;

    /**
     * Ignore javadoc attached artifacts from buildinfo generation.
     */
    @Parameter(property = "buildinfo.ignoreJavadoc", defaultValue = "true")
    private boolean ignoreJavadoc;

    /**
     * Artifacts to ignore, specified as a glob matching against <code>${groupId}/${filename}</code>, for example
     * <code>*</>/*.xml</code>.
     */
    @Parameter(property = "buildinfo.ignore", defaultValue = "")
    private List<String> ignore;

    /**
     * Detect projects/modules with install or deploy skipped: avoid taking fingerprints.
     */
    @Parameter(property = "buildinfo.detect.skip", defaultValue = "true")
    private boolean detectSkip;

    /**
     * Avoid taking fingerprints for modules specified as glob matching against <code>${groupId}/${artifactId}</code>.
     * @since 3.5.0
     */
    @Parameter(property = "buildinfo.skipModules")
    private List<String> skipModules;

    private List<PathMatcher> skipModulesMatcher = null;

    /**
     * Makes the generated {@code .buildinfo} file reproducible, by dropping detailed environment recording: OS will be
     * recorded as "Windows" or "Unix", JVM version as major version only.
     *
     * @since 3.1.0
     */
    @Parameter(property = "buildinfo.reproducible", defaultValue = "false")
    private boolean reproducible;

    /**
     * The current build session instance. This is used for toolchain manager API calls.
     */
    @Component
    protected MavenSession session;

    /**
     * Timestamp for reproducible output archive entries, either formatted as ISO 8601
     * <code>yyyy-MM-dd'T'HH:mm:ssXXX</code> or as an int representing seconds since the epoch (like
     * <a href="https://reproducible-builds.org/docs/source-date-epoch/">SOURCE_DATE_EPOCH</a>).
     *
     * @since 3.2.0
     */
    @Parameter(defaultValue = "${project.build.outputTimestamp}")
    private String outputTimestamp;

    /**
     * Diagnose {@code outputTimestamp} effective value based on execution context.
     *
     * @since 3.5.2
     */
    @Parameter(property = "diagnose", defaultValue = "false")
    private boolean diagnose;

    /**
     * To obtain a toolchain if possible.
     */
    @Component
    private ToolchainManager toolchainManager;

    @Component
    protected RuntimeInformation rtInformation;

    @Override
    public void execute() throws MojoExecutionException {
        boolean mono = session.getProjects().size() == 1;

        hasBadOutputTimestamp(outputTimestamp, getLog(), project, session.getProjects(), diagnose);

        if (!mono) {
            // if module skips install and/or deploy
            if (isSkip(project)) {
                getLog().info("Skipping goal because module skips install and/or deploy");
                return;
            }
            // if multi-module build, generate (aggregate) buildinfo only in last module
            MavenProject last = getLastProject();
            if (project != last) {
                skip(last);
                return;
            }
        }

        // generate buildinfo
        Map<Artifact, String> artifacts = generateBuildinfo(mono);
        getLog().info("Saved " + (mono ? "" : "aggregate ") + "info on build to " + buildinfoFile);

        copyAggregateToRoot(buildinfoFile);

        execute(artifacts);
    }

    static boolean hasBadOutputTimestamp(
            String outputTimestamp,
            Log log,
            MavenProject project,
            List<MavenProject> reactorProjects,
            boolean diagnose) {
        Instant timestamp =
                MavenArchiver.parseBuildOutputTimestamp(outputTimestamp).orElse(null);

        if (diagnose) {
            log.info("outputTimestamp = " + outputTimestamp + " => "
                    + ((timestamp == null) ? "disabled" : DateTimeFormatter.ISO_INSTANT.format(timestamp)));

            String projectProperty = project.getProperties().getProperty("project.build.outputTimestamp");
            String modelProperty = project.getModel().getProperties().getProperty("project.build.outputTimestamp");
            String originalModelProperty =
                    project.getOriginalModel().getProperties().getProperty("project.build.outputTimestamp");

            log.info("plugin outputTimestamp parameter diagnostics:" + System.lineSeparator()
                    + "        - plugin outputTimestamp parameter (defaultValue=\"${project.build.outputTimestamp}\") = "
                    + outputTimestamp + System.lineSeparator()
                    + "        - project project.build.outputTimestamp property = " + projectProperty
                    + System.lineSeparator()
                    + "        - model project.build.outputTimestamp property = " + modelProperty
                    + System.lineSeparator()
                    + "        - original model project.build.outputTimestamp property = " + originalModelProperty);
        }

        if (timestamp == null) {
            log.error("Reproducible Build not activated by project.build.outputTimestamp property: "
                    + "see https://maven.apache.org/guides/mini/guide-reproducible-builds.html");

            String projectProperty = project.getProperties().getProperty("project.build.outputTimestamp");
            if (projectProperty != null && projectProperty.startsWith("${git.")) {
                log.error("project.build.outputTimestamp = \"" + projectProperty + "\": isn't Git value set?");
                log.error("Did validate phase run and Git plugin set the value?");
                if (project.getPackaging().equals("pom")) {
                    log.error("if using git-commit-id-plugin, <skipPoms>false</skipPoms> may solve the issue.");
                }
            }
            return true;
        }

        if (log.isDebugEnabled()) {
            log.debug("project.build.outputTimestamp = \"" + outputTimestamp + "\" => "
                    + DateTimeFormatter.ISO_INSTANT.format(timestamp));
        }

        // check if timestamp defined in a project from reactor: info if it is not the case
        boolean parentInReactor = false;
        MavenProject reactorParent = project;
        while (reactorProjects.contains(reactorParent.getParent())) {
            parentInReactor = true;
            reactorParent = reactorParent.getParent();
        }
        String prop = reactorParent.getOriginalModel().getProperties().getProperty("project.build.outputTimestamp");
        if (prop == null) {
            log.info("<project.build.outputTimestamp> property (= " + outputTimestamp + ") is inherited"
                    + (parentInReactor ? " from outside the reactor" : "") + ", you can override in "
                    + (parentInReactor ? "parent POM from reactor " + reactorParent.getFile() : "pom.xml"));
            return false;
        }

        return false;
    }

    /**
     * Execute after buildinfo has been generated for current build (eventually aggregated).
     *
     * @param artifacts a Map of artifacts added to the build info with their associated property key prefix
     *         (<code>outputs.[#module.].#artifact</code>)
     */
    abstract void execute(Map<Artifact, String> artifacts) throws MojoExecutionException;

    protected void skip(MavenProject last) throws MojoExecutionException {
        getLog().info("Skipping intermediate goal run, aggregate will be " + last.getArtifactId());
    }

    protected void copyAggregateToRoot(File aggregate) throws MojoExecutionException {
        if (session.getProjects().size() == 1) {
            // mono-module, no aggregate file to deal with
            return;
        }

        // copy aggregate file to root target directory
        MavenProject root = getExecutionRoot();
        String extension = aggregate.getName().substring(aggregate.getName().lastIndexOf('.'));
        File rootCopy =
                new File(root.getBuild().getDirectory(), root.getArtifactId() + '-' + root.getVersion() + extension);
        try {
            rootCopy.getParentFile().mkdirs();
            Files.copy(
                    aggregate.toPath(),
                    rootCopy.toPath(),
                    LinkOption.NOFOLLOW_LINKS,
                    StandardCopyOption.REPLACE_EXISTING);
            getLog().info("Aggregate " + extension.substring(1) + " copied to " + rootCopy);
        } catch (IOException ioe) {
            throw new MojoExecutionException("Could not copy " + aggregate + " to " + rootCopy, ioe);
        }
    }

    /**
     * Generate buildinfo file.
     *
     * @param mono is it a mono-module build?
     * @return a Map of artifacts added to the build info with their associated property key prefix
     *         (<code>outputs.[#module.].#artifact</code>)
     * @throws MojoExecutionException if anything goes wrong
     */
    protected Map<Artifact, String> generateBuildinfo(boolean mono) throws MojoExecutionException {
        MavenProject root = mono ? project : getExecutionRoot();

        buildinfoFile.getParentFile().mkdirs();

        try (PrintWriter p = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(Files.newOutputStream(buildinfoFile.toPath()), StandardCharsets.UTF_8)))) {
            BuildInfoWriter bi = new BuildInfoWriter(getLog(), p, mono, rtInformation);
            bi.setIgnoreJavadoc(ignoreJavadoc);
            bi.setIgnore(ignore);
            bi.setToolchain(getToolchain());

            bi.printHeader(root, mono ? null : project, reproducible);

            // artifact(s) fingerprints
            if (mono) {
                bi.printArtifacts(project);
            } else {
                for (MavenProject project : session.getProjects()) {
                    if (!isSkip(project)) {
                        bi.printArtifacts(project);
                    }
                }
            }

            if (p.checkError()) {
                throw new MojoExecutionException("Write error to " + buildinfoFile);
            }

            return bi.getArtifacts();
        } catch (IOException e) {
            throw new MojoExecutionException("Error creating file " + buildinfoFile, e);
        }
    }

    protected MavenProject getExecutionRoot() {
        for (MavenProject p : session.getProjects()) {
            if (p.isExecutionRoot()) {
                return p;
            }
        }
        return null;
    }

    private MavenProject getLastProject() {
        int i = session.getProjects().size();
        while (i > 0) {
            MavenProject project = session.getProjects().get(--i);
            if (!isSkip(project)) {
                return project;
            }
        }
        return null;
    }

    private boolean isSkip(MavenProject project) {
        // manual/configured module skip
        boolean skipModule = false;
        if (skipModules != null && !skipModules.isEmpty()) {
            if (skipModulesMatcher == null) {
                FileSystem fs = FileSystems.getDefault();
                skipModulesMatcher = skipModules.stream()
                        .map(i -> fs.getPathMatcher("glob:" + i))
                        .collect(Collectors.toList());
            }
            Path path = Paths.get(project.getGroupId() + '/' + project.getArtifactId());
            skipModule = skipModulesMatcher.stream().anyMatch(m -> m.matches(path));
        }
        // detected skip
        return skipModule || (detectSkip && PluginUtil.isSkip(project));
    }

    private Toolchain getToolchain() {
        Toolchain tc = null;
        if (toolchainManager != null) {
            tc = toolchainManager.getToolchainFromBuildContext("jdk", session);
        }

        return tc;
    }
}

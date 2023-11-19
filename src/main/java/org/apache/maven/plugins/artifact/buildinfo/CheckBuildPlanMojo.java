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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.LifecycleExecutor;
import org.apache.maven.lifecycle.MavenExecutionPlan;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Check from buildplan that plugins used don't have known Reproducible Builds issues.
 *
 * @since 3.3.0
 */
@Mojo(name = "check-buildplan", threadSafe = true, requiresProject = true)
public class CheckBuildPlanMojo extends AbstractMojo {
    @Parameter(defaultValue = "${reactorProjects}", required = true, readonly = true)
    private List<MavenProject> reactorProjects;

    @Component
    private MavenProject project;

    @Component
    private MavenSession session;

    @Component
    private LifecycleExecutor lifecycleExecutor;

    /** Allow to specify which goals/phases will be used to calculate execution plan. */
    @Parameter(property = "check.buildplan.tasks", defaultValue = "deploy")
    private String[] tasks;

    /**
     * Timestamp for reproducible output archive entries, either formatted as ISO 8601
     * <code>yyyy-MM-dd'T'HH:mm:ssXXX</code> or as an int representing seconds since the epoch (like
     * <a href="https://reproducible-builds.org/docs/source-date-epoch/">SOURCE_DATE_EPOCH</a>).
     */
    @Parameter(defaultValue = "${project.build.outputTimestamp}")
    private String outputTimestamp;

    /**
     * Provide a plugin issues property file to override plugin's <code>not-reproducible-plugins.properties</code>.
     */
    @Parameter(property = "check.plugin-issues")
    private File pluginIssues;

    /**
     * Make build fail if execution plan contains non-reproducible plugins.
     */
    @Parameter(property = "check.failOnNonReproducible", defaultValue = "true")
    private boolean failOnNonReproducible;

    protected MavenExecutionPlan calculateExecutionPlan() throws MojoExecutionException {
        try {
            return lifecycleExecutor.calculateExecutionPlan(session, tasks);
        } catch (Exception e) {
            throw new MojoExecutionException("Cannot calculate Maven execution plan" + e.getMessage(), e);
        }
    }

    @Override
    public void execute() throws MojoExecutionException {
        boolean fail = hasBadOutputTimestamp();
        // TODO check maven-jar-plugin module-info.class?

        Properties issues = loadIssues();

        MavenExecutionPlan plan = calculateExecutionPlan();

        Set<String> plugins = new HashSet<>();
        for (MojoExecution exec : plan.getMojoExecutions()) {
            Plugin plugin = exec.getPlugin();
            String id = plugin.getId();

            if (plugins.add(id)) {
                // check reproducibility status
                String issue = issues.getProperty(plugin.getKey());
                if (issue == null) {
                    getLog().info("no known issue with " + id);
                } else if (issue.startsWith("fail:")) {
                    String logMessage = "plugin without solution " + id + ", see " + issue.substring(5);
                    if (failOnNonReproducible) {
                        getLog().error(logMessage);
                    } else {
                        getLog().warn(logMessage);
                    }
                    fail = true;

                } else {
                    ArtifactVersion minimum = new DefaultArtifactVersion(issue);
                    ArtifactVersion version = new DefaultArtifactVersion(plugin.getVersion());
                    if (version.compareTo(minimum) < 0) {
                        String logMessage = "plugin with non-reproducible output: " + id + ", require minimum " + issue;
                        if (failOnNonReproducible) {
                            getLog().error(logMessage);
                        } else {
                            getLog().warn(logMessage);
                        }
                        fail = true;
                    } else {
                        getLog().info("no known issue with " + id + " (>= " + issue + ")");
                    }
                }
            }
        }

        if (fail) {
            getLog().info("current module pom.xml is " + project.getBasedir() + "/pom.xml");
            MavenProject parent = project;
            while (true) {
                parent = parent.getParent();
                if ((parent == null) || !reactorProjects.contains(parent)) {
                    break;
                }
                getLog().info("        parent pom.xml is " + parent.getBasedir() + "/pom.xml");
            }
            String message = "non-reproducible plugin or configuration found with fix available";
            if (failOnNonReproducible) {
                throw new MojoExecutionException(message);
            } else {
                getLog().warn(message);
            }
        }
    }

    private boolean hasBadOutputTimestamp() {
        MavenArchiver archiver = new MavenArchiver();
        Date timestamp = archiver.parseOutputTimestamp(outputTimestamp);
        if (timestamp == null) {
            getLog().error("Reproducible Build not activated by project.build.outputTimestamp property: "
                    + "see https://maven.apache.org/guides/mini/guide-reproducible-builds.html");
            return true;
        } else {
            if (getLog().isDebugEnabled()) {
                getLog().debug("project.build.outputTimestamp = \"" + outputTimestamp + "\" => "
                        + new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(timestamp));
            }

            // check if timestamp well defined in a project from reactor
            boolean parentInReactor = false;
            MavenProject reactorParent = project;
            while (reactorProjects.contains(reactorParent.getParent())) {
                parentInReactor = true;
                reactorParent = reactorParent.getParent();
            }
            String prop = reactorParent.getOriginalModel().getProperties().getProperty("project.build.outputTimestamp");
            if (prop == null) {
                getLog().warn(
                                "The project.build.outputTimestamp property should not be inherited from outside this project. "
                                        + "It should be defined in the "
                                        + (parentInReactor ? "local parent POM " : "POM ")
                                        + reactorParent.getFile());
                return false;
            }
        }
        return false;
    }

    private Properties loadIssues() throws MojoExecutionException {
        try (InputStream in = (pluginIssues == null)
                ? getClass().getResourceAsStream("not-reproducible-plugins.properties")
                : Files.newInputStream(pluginIssues.toPath())) {
            Properties prop = new Properties();
            prop.load(in);

            Properties result = new Properties();
            for (Map.Entry<Object, Object> entry : prop.entrySet()) {
                String plugin = entry.getKey().toString().replace('+', ':');
                if (!plugin.contains(":")) {
                    plugin = "org.apache.maven.plugins:" + plugin;
                }
                result.put(plugin, entry.getValue());
            }
            return result;
        } catch (IOException ioe) {
            throw new MojoExecutionException("Cannot load issues file", ioe);
        }
    }
}

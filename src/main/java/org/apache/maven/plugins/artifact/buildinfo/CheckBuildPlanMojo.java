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
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

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
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionScheme;

/**
 * Check from buildplan that plugins used don't have known Reproducible Builds issues.
 *
 * @since 3.3.0
 */
@Mojo(name = "check-buildplan", threadSafe = true, requiresProject = true)
public class CheckBuildPlanMojo extends AbstractMojo {
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

    private final VersionScheme versionScheme = new GenericVersionScheme();

    protected MavenExecutionPlan calculateExecutionPlan() throws MojoExecutionException {
        try {
            return lifecycleExecutor.calculateExecutionPlan(session, tasks);
        } catch (Exception e) {
            throw new MojoExecutionException("Cannot calculate Maven execution plan" + e.getMessage(), e);
        }
    }

    @Override
    public void execute() throws MojoExecutionException {
        boolean fail =
                AbstractBuildinfoMojo.hasBadOutputTimestamp(outputTimestamp, getLog(), project, session.getProjects());

        // TODO check maven-jar-plugin module-info.class?

        Properties issues = loadIssues();

        MavenExecutionPlan plan = calculateExecutionPlan();

        Set<String> plugins = new HashSet<>();
        int okCount = 0;
        for (MojoExecution exec : plan.getMojoExecutions()) {
            Plugin plugin = exec.getPlugin();
            String id = plugin.getId();

            if (plugins.add(id)) {
                // check reproducibility status
                String issue = issues.getProperty(plugin.getKey());
                if (issue == null) {
                    okCount++;
                    getLog().debug("No known issue with " + id);
                } else if (issue.startsWith("fail:")) {
                    String logMessage = "plugin without solution " + id + ", see " + issue.substring(5);
                    if (failOnNonReproducible) {
                        getLog().error(logMessage);
                    } else {
                        getLog().warn(logMessage);
                    }
                    fail = true;
                } else {
                    try {
                        Version minimum = versionScheme.parseVersion(issue);
                        Version version = versionScheme.parseVersion(plugin.getVersion());
                        if (version.compareTo(minimum) < 0) {
                            String logMessage =
                                    "plugin with non-reproducible output: " + id + ", require minimum " + issue;
                            if (failOnNonReproducible) {
                                getLog().error(logMessage);
                            } else {
                                getLog().warn(logMessage);
                            }
                            fail = true;
                        } else {
                            okCount++;
                            getLog().debug("No known issue with " + id + " (>= " + issue + ")");
                        }
                        fail = true;
                    } catch (InvalidVersionSpecificationException e) {
                        throw new MojoExecutionException(e);
                    }
                }
            }
        }
        if (okCount > 0) {
            getLog().info("No known issue in " + okCount + " plugins");
        }

        if (fail) {
            getLog().info("current module pom.xml is " + project.getBasedir() + "/pom.xml");
            MavenProject parent = project;
            while (true) {
                parent = parent.getParent();
                if ((parent == null) || !session.getProjects().contains(parent)) {
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

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

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginUtilTest {

    @Test
    void isSkipDetectsSkipConfiguredOnAnExplicitExecutionId() {
        // Reproduces GitHub #150 (MARTIFACT-45): <skip>true</skip> set on an execution with an explicit id
        // (e.g. the implicit default-deploy execution) was only ever matched at the plugin level, so it was
        // silently ignored.
        MavenProject project = projectWithDeployPlugin(deployPluginWithSkipExecution("default-deploy"));

        assertTrue(PluginUtil.isSkip(project));
    }

    @Test
    void isSkipStillDetectsSkipConfiguredAtThePluginLevel() {
        Plugin deployPlugin = new Plugin();
        deployPlugin.setGroupId("org.apache.maven.plugins");
        deployPlugin.setArtifactId("maven-deploy-plugin");
        deployPlugin.setConfiguration(skipDom());

        assertTrue(PluginUtil.isSkip(projectWithDeployPlugin(deployPlugin)));
    }

    @Test
    void isSkipIsFalseWhenNoExecutionConfiguresSkip() {
        Plugin deployPlugin = new Plugin();
        deployPlugin.setGroupId("org.apache.maven.plugins");
        deployPlugin.setArtifactId("maven-deploy-plugin");
        deployPlugin.addExecution(new PluginExecution());

        assertFalse(PluginUtil.isSkip(projectWithDeployPlugin(deployPlugin)));
    }

    private static MavenProject projectWithDeployPlugin(Plugin deployPlugin) {
        Build build = new Build();
        build.addPlugin(deployPlugin);
        build.setPluginManagement(new PluginManagement());

        Model model = new Model();
        model.setBuild(build);

        return new MavenProject(model);
    }

    private static Plugin deployPluginWithSkipExecution(String executionId) {
        PluginExecution execution = new PluginExecution();
        execution.setId(executionId);
        execution.setConfiguration(skipDom());

        Plugin deployPlugin = new Plugin();
        deployPlugin.setGroupId("org.apache.maven.plugins");
        deployPlugin.setArtifactId("maven-deploy-plugin");
        deployPlugin.addExecution(execution);
        return deployPlugin;
    }

    private static Xpp3Dom skipDom() {
        Xpp3Dom configuration = new Xpp3Dom("configuration");
        Xpp3Dom skip = new Xpp3Dom("skip");
        skip.setValue("true");
        configuration.addChild(skip);
        return configuration;
    }
}

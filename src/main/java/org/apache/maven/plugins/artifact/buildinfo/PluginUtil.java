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

import java.util.Map;

import org.apache.maven.model.ConfigurationContainer;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Plugin utility to detect if install or deploy is skipped in a build, or even nexus-staging.
 * It supports both disabling by parameter configuration or property.
 */
class PluginUtil {
    private static final String NEXUS_STAGING = "nexus-staging";
    private static final String CENTRAL_PUBLISHING = "central-publishing";

    static boolean isSkip(MavenProject project) {
        // check the extension plugins first, as using them drops deploy
        if (getPlugin(project, "org.sonatype.plugins:" + NEXUS_STAGING + "-maven-plugin") != null) {
            return isSkip(project, NEXUS_STAGING);
        }
        if (getPlugin(project, "org.sonatype.central:" + CENTRAL_PUBLISHING + "-maven-plugin") != null) {
            return isSkip(project, CENTRAL_PUBLISHING);
        }
        // install/deploy are really used
        return isSkip(project, "install") || isSkip(project, "deploy");
    }

    private static boolean isSkip(MavenProject project, String id) {
        String pluginGa;
        String pluginParameter;
        String pluginProperty;
        if (id.equals(NEXUS_STAGING)) {
            pluginGa = "org.sonatype.plugins:" + id + "-maven-plugin";
            pluginParameter = "skipNexusStagingDeployMojo";
            pluginProperty = "skipNexusStagingDeployMojo";
        } else if (id.equals(CENTRAL_PUBLISHING)) {
            pluginGa = "org.sonatype.central:" + id + "-maven-plugin";
            pluginParameter = "skipPublishing";
            pluginProperty = "skipPublishing";
        } else {
            pluginGa = "org.apache.maven.plugins:maven-" + id + "-plugin";
            pluginParameter = "skip";
            pluginProperty = "maven." + id + ".skip";
        }

        Plugin plugin = getPlugin(project, pluginGa);
        String skip = getConfigurationParameter(plugin, pluginParameter);
        if (skip == null) {
            skip = getExecutionsParameter(plugin, pluginParameter);
        }
        if (skip == null) {
            skip = project.getProperties().getProperty(pluginProperty);
        }
        return Boolean.parseBoolean(skip);
    }

    private static Plugin getPlugin(MavenProject project, String plugin) {
        Map<String, Plugin> pluginsAsMap = project.getBuild().getPluginsAsMap();
        Plugin result = pluginsAsMap.get(plugin);
        if (result == null) {
            pluginsAsMap = project.getPluginManagement().getPluginsAsMap();
            result = pluginsAsMap.get(plugin);
        }
        return result;
    }

    private static String getExecutionsParameter(Plugin plugin, String parameter) {
        if (plugin == null) {
            return null;
        }
        for (PluginExecution execution : plugin.getExecutions()) {
            String value = getConfigurationParameter(execution, parameter);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String getConfigurationParameter(ConfigurationContainer container, String parameter) {
        if (container != null) {
            Xpp3Dom conf = (Xpp3Dom) container.getConfiguration();

            if (conf != null) {
                Xpp3Dom target = conf.getChild(parameter);

                if (target != null) {
                    return target.getValue();
                }
            }
        }

        return null;
    }
}

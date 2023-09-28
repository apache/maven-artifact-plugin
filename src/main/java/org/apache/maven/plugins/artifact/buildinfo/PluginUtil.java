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

import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Plugin utility to detect if install or deploy is skipped in a build, or even nexus-staging.
 * It supports both disabling by parameter configuration or property.
 * Known limitation: it does not look at configuration done more precisely at plugin execution level.
 */
class PluginUtil {
    private static final String NEXUS_STAGING = "nexus-staging";

    static boolean isSkip(MavenProject project) {
        return isSkip(project, "install") || isSkip(project, "deploy") || isSkip(project, NEXUS_STAGING);
    }

    private static boolean isSkip(MavenProject project, String id) {
        String pluginGa;
        String pluginParameter;
        String pluginProperty;
        if (id.equals(NEXUS_STAGING)) {
            pluginGa = "org.sonatype.plugins:" + id + "-maven-plugin";
            pluginParameter = "skipNexusStagingDeployMojo";
            pluginProperty = "skipNexusStagingDeployMojo";
        } else {
            pluginGa = "org.apache.maven.plugins:maven-" + id + "-plugin";
            pluginParameter = "skip";
            pluginProperty = "maven." + id + ".skip";
        }

        Plugin plugin = getPlugin(project, pluginGa);
        String skip = getPluginParameter(plugin, pluginParameter);
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

    private static String getPluginParameter(Plugin plugin, String parameter) {
        if (plugin != null) {
            Xpp3Dom pluginConf = (Xpp3Dom) plugin.getConfiguration();

            if (pluginConf != null) {
                Xpp3Dom target = pluginConf.getChild(parameter);

                if (target != null) {
                    return target.getValue();
                }
            }
        }

        return null;
    }
}

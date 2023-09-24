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
 * Plugin utility to detect if install or deploy is skipped in a build.
 */
class PluginUtil {
    static boolean isSkip(MavenProject project) {
        return isSkip(project, "install") || isSkip(project, "deploy");
    }

    private static boolean isSkip(MavenProject project, String id) {
        Plugin plugin = getPlugin(project, "org.apache.maven.plugins:maven-" + id + "-plugin");
        String skip = getPluginParameter(plugin, "skip");
        if (skip == null) {
            skip = project.getProperties().getProperty("maven." + id + ".skip");
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

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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

/**
 * Test class to generate {@code target/generated-site/apt/plugin-issues.apt} with content extracted from
 * {@code src/main/resources/org/apache/maven/plugins/artifact/buildinfo/not-reproducible-plugins.properties}
 * adn generated from {@code src/test/resources/plugin-issues.apt}.
 */
class NotReproduciblePluginsDocumentationTest {
    private static final String LS = System.lineSeparator();

    @Test
    void basic() throws Exception {
        File pluginIssuesApt = new File("src/test/resources/plugin-issues.apt");
        String content = new String(Files.readAllBytes(pluginIssuesApt.toPath()), StandardCharsets.UTF_8);

        File targetDirectory = new File("target/generated-site/apt");
        targetDirectory.mkdirs();
        pluginIssuesApt = new File(targetDirectory, pluginIssuesApt.getName());

        StringBuilder sb = new StringBuilder(content);
        sb.append(LS);
        sb.append(
                "*---------+-------------------------------------------------------------------+-------+--------------+"
                        + LS);
        sb.append(
                "|  | <<plugin>>                                                 | <<minimum version>> | <<comments>>");
        String groupId = null;
        for (String line : Files.readAllLines(new File(
                        "src/main/resources/org/apache/maven/plugins/artifact/buildinfo/not-reproducible-plugins.properties")
                .toPath())) {
            if (line.trim().isEmpty()) {
                continue;
            }
            if (!line.startsWith("#")) {
                sb.append(LS
                        + "*--------+--------------------------------------------------------------------+-------+--------------+"
                        + LS);
                int index = line.indexOf('=');
                String plugin = line.substring(0, index);
                String status = line.substring(index + 1);

                index = plugin.indexOf('+');
                if (index < 0) {
                    groupId = "org.apache.maven.plugins";
                    sb.append("| org.apache.maven.plugins | {{{/plugins/" + plugin + "/}" + plugin + "}} ");
                } else {
                    groupId = plugin.substring(0, index);
                    plugin = plugin.substring(index + 1);
                    sb.append("| " + groupId + " | " + plugin + " ");
                }
                if (status.startsWith("fail:")) {
                    sb.append("| - | no fixed release available, see {{{" + status.substring(5) + "}reference}}");
                } else {
                    sb.append("| " + status + " | ");
                }
                continue;
            }
            if (groupId == null) {
                continue;
            }
            if (!line.startsWith("##")) {
                sb.append(' ');
                line = line.substring(1).trim();
                if (line.startsWith("https://")) {
                    String url = line;
                    if (url.contains("/jira/browse/")) {
                        line = line.substring(url.indexOf("/jira/browse/") + 13);
                    } else if (url.startsWith("https://github.com/")) {
                        line = line.substring(19);
                    }
                    sb.append("{{{" + url + "}" + line + "}}");
                } else {
                    sb.append(line);
                }
            }
        }
        sb.append(LS
                + "*----------+------------------------------------------------------------------+-------+--------------+"
                + LS);

        Files.write(pluginIssuesApt.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}

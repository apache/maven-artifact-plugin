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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReproducibleCentralReportTest {
    @Test
    void artifactUrlEncodesCoordinates() {
        assertEquals(
                "https://jvm-repo-rebuild.github.io/reproducible-central/badge/artifact/"
                        + "com%26pany/example/my%24artifact.html",
                ReproducibleCentralReport.reproducibleCentralArtifactUrl("com&pany.example", "my$artifact"));
    }

    @Test
    void artifactBadgeUrlEncodesCoordinates() {
        assertEquals(
                "https://img.shields.io/reproducible-central/artifact/com%26pany/my%24artifact/"
                        + "1.0%2FRC%3F1%23x%22%26?labelColor=1e5b96",
                ReproducibleCentralReport.reproducibleCentralArtifactBadgeUrl(
                        "com&pany", "my$artifact", "1.0/RC?1#x\"&"));
    }
}

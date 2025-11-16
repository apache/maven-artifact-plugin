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

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Simple smoke test that checks the generated Reproducible Central report contains scope headings.
 *
 * Note: this test assumes the site has been generated and the report exists at
 * target/site/reproducible-central.html. This mirrors existing project tests that operate on
 * generated-site/target outputs.
 */
public class ReproducibleCentralReportSmokeTest {
    @Test
    public void testScopeHeadingsPresent() throws Exception {
        File report = new File("target/site/reproducible-central.html");
        assertTrue("Reproducible Central report should exist: " + report.getPath(), report.exists());

        String content = new String(Files.readAllBytes(report.toPath()), StandardCharsets.UTF_8);

        assertTrue("Should contain compile heading", content.contains("<h2>compile dependencies:</h2>"));
        assertTrue("Should contain provided heading", content.contains("<h2>provided dependencies:</h2>"));
        assertTrue("Should contain runtime heading", content.contains("<h2>runtime dependencies:</h2>"));
        assertTrue("Should contain test heading", content.contains("<h2>test dependencies:</h2>"));
    }
}

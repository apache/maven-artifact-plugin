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

import java.nio.file.PathMatcher;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractBuildinfoMojoTest {

    @Test
    void invalidGlobPatternIsReportedWithClearError() {
        MojoExecutionException e = assertThrows(
                MojoExecutionException.class,
                () -> AbstractBuildinfoMojo.compileGlobs(
                        Collections.singletonList("org.example/["), "buildinfo.ignore"));

        assertTrue(e.getMessage().contains("buildinfo.ignore"));
        assertTrue(e.getMessage().contains("org.example/["));
    }

    @Test
    void validGlobPatternsCompile() throws MojoExecutionException {
        List<PathMatcher> matchers =
                AbstractBuildinfoMojo.compileGlobs(Arrays.asList("*/*.xml", "com.example/*"), "buildinfo.ignore");

        assertEquals(2, matchers.size());
    }
}

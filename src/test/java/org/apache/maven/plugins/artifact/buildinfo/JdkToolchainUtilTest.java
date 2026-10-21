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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.apache.maven.toolchain.Toolchain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The fake {@code java} executables are shell scripts, so these tests don't run on Windows.
 */
@DisabledOnOs(OS.WINDOWS)
class JdkToolchainUtilTest {
    @TempDir
    Path tempDir;

    @Test
    void readsVersionOfJavaInstalledInDirectoryWithSpaces() throws IOException {
        Path java = fakeJava(tempDir.resolve("My JDKs"), "echo 'openjdk version \"17.0.2\"' >&2");

        String version = JdkToolchainUtil.getJavaVersion(toolchainWith(java));

        assertEquals("openjdk version \"17.0.2\"", version);
    }

    private static Path fakeJava(Path jdkHome, String script) throws IOException {
        Path bin = Files.createDirectories(jdkHome.resolve("bin"));
        Path java = bin.resolve("java");
        Files.write(java, Arrays.asList("#!/bin/sh", script));
        if (!java.toFile().setExecutable(true)) {
            throw new IOException("Cannot make " + java + " executable");
        }
        return java;
    }

    private static Toolchain toolchainWith(Path java) {
        return new Toolchain() {
            @Override
            public String getType() {
                return "jdk";
            }

            @Override
            public String findTool(String toolName) {
                return "java".equals(toolName) ? java.toString() : null;
            }
        };
    }
}

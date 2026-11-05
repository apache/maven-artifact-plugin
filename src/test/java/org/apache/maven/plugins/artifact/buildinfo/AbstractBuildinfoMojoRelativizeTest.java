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

import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbstractBuildinfoMojoRelativizeTest {

    private static AbstractBuildinfoMojo newMojo(File basedir) {
        MavenProject project = new MavenProject();
        project.setFile(new File(basedir, "pom.xml"));
        MavenSession session =
                new MavenSession(null, new DefaultMavenExecutionRequest(), new DefaultMavenExecutionResult(), project);
        return new BuildinfoMojo(null, null, null, project, session, null);
    }

    @Test
    void relativizesFileUnderBasedir() {
        File basedir = new File("/a/b");
        assertEquals(
                "target" + File.separator + "app.jar", newMojo(basedir).relative(new File(basedir, "target/app.jar")));
    }

    @Test
    void doesNotTreatSiblingDirectoryWithSharedPrefixAsUnderBasedir() {
        // basedir=/a/b, file=/a/bc/d: a naive string prefix-strip would wrongly return "d"
        File basedir = new File("/a/b");
        assertEquals(
                ".." + File.separator + "bc" + File.separator + "d",
                newMojo(basedir).relative(new File("/a/bc/d")));
    }

    @Test
    void doesNotThrowWhenFileIsShorterThanBasedir() {
        // a naive substring(length + 1) throws StringIndexOutOfBoundsException here
        File basedir = new File("/a/b");
        assertEquals("..", newMojo(basedir).relative(new File("/a")));
    }

    @Test
    void fallsBackInsteadOfThrowingWhenOneSideIsNotAbsolute() {
        // Path#relativize throws IllegalArgumentException when exactly one of the two paths is absolute.
        // relative() must recover by retrying with both paths made absolute, rather than propagate it.
        File basedir = new File("/a/b");
        File file = new File("relative/child.jar");

        String result = newMojo(basedir).relative(file);

        String expected = basedir.toPath()
                .toAbsolutePath()
                .normalize()
                .relativize(file.toPath().toAbsolutePath().normalize())
                .toString();
        assertEquals(expected, result);
    }
}

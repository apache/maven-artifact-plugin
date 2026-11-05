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
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AbstractBuildinfoMojoRelativizeTest {
    @Test
    void relativizesFileUnderBasedir() {
        assertEquals(
                "target" + File.separator + "app.jar",
                AbstractBuildinfoMojo.relativize(Paths.get("/a/b"), Paths.get("/a/b/target/app.jar")));
    }

    @Test
    void doesNotTreatSiblingDirectoryWithSharedPrefixAsUnderBasedir() {
        // basedir=/a/b, file=/a/bc/d: a naive string prefix-strip would wrongly return "d"
        assertEquals(
                ".." + File.separator + "bc" + File.separator + "d",
                AbstractBuildinfoMojo.relativize(Paths.get("/a/b"), Paths.get("/a/bc/d")));
    }

    @Test
    void doesNotThrowWhenFileIsShorterThanBasedir() {
        // a naive substring(length + 1) throws StringIndexOutOfBoundsException here
        assertEquals("..", AbstractBuildinfoMojo.relativize(Paths.get("/a/b"), Paths.get("/a")));
    }

    @Test
    void fallsBackInsteadOfThrowingWhenOneSideIsNotAbsolute() {
        // Path#relativize throws IllegalArgumentException when exactly one of the two paths is absolute.
        // relativize() must recover by retrying with both paths made absolute, rather than propagate it.
        Path basedir = Paths.get("/a/b");
        Path file = Paths.get("relative/child.jar");

        String result = assertDoesNotThrow(() -> AbstractBuildinfoMojo.relativize(basedir, file));

        Path expected = basedir.toAbsolutePath()
                .normalize()
                .relativize(file.toAbsolutePath().normalize());
        assertEquals(expected.toString(), result);
    }
}

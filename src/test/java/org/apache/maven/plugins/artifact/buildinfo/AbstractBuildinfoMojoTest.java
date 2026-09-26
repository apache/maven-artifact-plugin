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

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractBuildinfoMojoTest {

    @Test
    void invalidSkipModulesGlobResultsInClearError() throws Exception {
        AbstractBuildinfoMojo mojo = newMojo(null, Collections.singletonList("org.example/["));

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);

        assertTrue(e.getMessage().contains("buildinfo.skipModules"));
        assertTrue(e.getMessage().contains("org.example/["));
    }

    @Test
    void invalidIgnoreGlobResultsInClearError() throws Exception {
        AbstractBuildinfoMojo mojo = newMojo(Collections.singletonList("org.example/["), null);

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);

        assertTrue(e.getMessage().contains("buildinfo.ignore"));
        assertTrue(e.getMessage().contains("org.example/["));
    }

    @Test
    void validSkipModulesGlobsAreUsedForSkipping() throws Exception {
        AbstractBuildinfoMojo mojo = newMojo(null, Collections.singletonList("com.example/*"));

        mojo.initializeGlobMatchers();

        MavenProject matching = new MavenProject();
        matching.setGroupId("com.example");
        matching.setArtifactId("sample");
        assertTrue(mojo.isSkipModule(matching));

        MavenProject other = new MavenProject();
        other.setGroupId("org.other");
        other.setArtifactId("sample");
        assertFalse(mojo.isSkipModule(other));
    }

    private static AbstractBuildinfoMojo newMojo(List<String> ignore, List<String> skipModules) throws Exception {
        DescribeBuildOutputMojo mojo = new DescribeBuildOutputMojo(null, null, null, null, null);
        setField(mojo, "ignore", ignore);
        setField(mojo, "skipModules", skipModules);
        return mojo;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = AbstractBuildinfoMojo.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}

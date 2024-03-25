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

import java.util.concurrent.atomic.AtomicReference;

import org.apache.maven.model.Model;
import org.apache.maven.monitor.logging.DefaultLog;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AbstractBuildinfoMojoTest {
    @Test
    void detectInjectedTimestamp() {
        final Model model = new Model();
        final MavenProject project = new MavenProject();
        project.setOriginalModel(model);
        project.getProperties().setProperty("project.build.outputTimestamp", "set");
        model.setProperties(project.getProperties());

        final AtomicReference<CharSequence> message = new AtomicReference<>();
        AbstractBuildinfoMojo.hasBadOutputTimestamp(
                null,
                new DefaultLog(null) {
                    @Override
                    public boolean isDebugEnabled() {
                        return false;
                    }

                    @Override
                    public void info(final CharSequence content) {
                        if (message.get() == null) {
                            message.set(content);
                        } else {
                            throw new IllegalStateException(content + " can't be set cause it is unexpected");
                        }
                    }
                },
                project,
                emptyList());

        assertEquals("project.build.outputTimestamp is injected by the build", message.get());
    }
}

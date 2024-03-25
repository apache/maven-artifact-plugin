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
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.toolchain.Toolchain;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

/**
 * A helper to get JDK version from a JDK toolchain.
 */
class JdkToolchainUtil {
    static String getJavaVersion(Toolchain toolchain) {
        String version = "unknown";
        String java = toolchain.findTool("java");
        if (java != null) {
            try {
                Commandline cl = new Commandline(java + " -version");
                LineConsumer out = new LineConsumer();
                LineConsumer err = new LineConsumer();
                CommandLineUtils.executeCommandLine(cl, out, err);
                version = String.join(":", err.getLines());
                if (version == null || version.trim().isEmpty()) {
                    version = "unable to detect...";
                }
            } catch (CommandLineException cle) {
                version = cle.toString();
            }
        }
        return version;
    }

    private static class LineConsumer implements StreamConsumer {
        private final List<String> lines = new ArrayList<>();

        @Override
        public void consumeLine(String line) throws IOException {
            lines.add(line);
        }

        List<String> getLines() {
            return lines;
        }
    }
}

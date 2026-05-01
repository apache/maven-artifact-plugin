
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

def buildLog = new File(basedir, 'build.log').text

assert buildLog.contains('[WARNING] Version specification with a range found in dependencies may make the project build non-reproducible')

// The following dependencies have been resolved from version ranges which may change over time and make the build non-reproducible
// so check only the dependencies with version ranges and not the ones with fixed versions
assert buildLog.contains(' - Dependency io.cucumber:messages:jar:32.')
assert buildLog.contains(' via io.cucumber:gherkin:jar:38.0.0 has been resolved from a version range [32.0.0,33.0.0)')

assert buildLog.contains(' - Dependency commons-io:commons-io:jar:2.21.0 (compile) has been resolved from a version range [2.20.0,2.21.0]')
assert buildLog.contains(' - Dependency commons-collections:commons-collections:jar:LATEST (compile) has been resolved from a version range LATEST')

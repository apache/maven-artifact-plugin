
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

// https://issues.apache.org/jira/browse/MARTIFACT-24 & MARTIFACT-53 & MARTIFACT-71
//assert buildLog.contains('[INFO] <project.build.outputTimestamp> property (= 2022-12-11T20:07:23Z) is inherited, you can override in pom.xml')
// Afaik it's not possible to get a property from a parent when the own project (here: maven-artifact-plugin) overwrites it.
// So it's not possible to pass the outputTimestamp property from maven-parent to the invoker-plugin as a script variable to be a checked here.
// Therefore the string to be checked is divided to not contain a fixed timestamp.
assert buildLog.contains("[INFO] <project.build.outputTimestamp> property (= ")
assert buildLog.contains(" is inherited, you can override in pom.xml")
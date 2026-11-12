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

// check existence of generated compare in target
File compareFile = new File( basedir, "target/missing-artifact-1.0-SNAPSHOT.buildcompare" );
assert compareFile.isFile()

Properties compare = new Properties()
compareFile.withInputStream { compare.load( it ) }

assert compare.getProperty( "version" ) == "1.0-SNAPSHOT"

// the main jar always differs (non-reproducible jar plugin version): 1 "ko"
// the extra classifier jar was never part of the reference build: 1 "missing"
// they must be reported as disjoint counts, not double-counted into ko
assert compare.getProperty( "ko" ) == "1"
assert compare.getProperty( "missing" ) == "1"
assert compare.getProperty( "koFiles" ) == '"missing-artifact-1.0-SNAPSHOT.jar"'
assert compare.getProperty( "missingFiles" ) == '"missing-artifact-1.0-SNAPSHOT-extra.jar"'

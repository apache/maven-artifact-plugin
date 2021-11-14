
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

String compare = new File( basedir, 'target/flatten-1.0-SNAPSHOT.buildcompare' ).text
assert compare.contains( 'okFiles="flatten-1.0-SNAPSHOT.pom flatten-modB-1.0-SNAPSHOT.pom flatten-modA-1.0-SNAPSHOT.pom"' )

assert new File( basedir, 'flattened-pom.xml' ).text == new File( basedir, 'target/reference/flatten-1.0-SNAPSHOT.pom' ).text
assert new File( basedir, 'modA/flattened-pom.xml' ).text == new File( basedir, 'target/reference/flatten-modA-1.0-SNAPSHOT.pom' ).text
assert new File( basedir, 'modB/pom.xml' ).text == new File( basedir, 'target/reference/flatten-modB-1.0-SNAPSHOT.pom' ).text

String buildinfo = new File( basedir, 'target/flatten-1.0-SNAPSHOT.buildinfo' ).text
assert buildinfo.contains( "outputs.0.0.length=" + new File( basedir, 'flattened-pom.xml' ).size() )
assert buildinfo.contains( "outputs.1.0.length=" + new File( basedir, 'modB/pom.xml' ).size() )
assert buildinfo.contains( "outputs.2.0.length=" + new File( basedir, 'modA/flattened-pom.xml' ).size() )

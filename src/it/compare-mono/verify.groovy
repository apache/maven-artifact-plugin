
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
File compareFile = new File( basedir, "target/mono-1.0-SNAPSHOT.buildcompare" );
assert compareFile.isFile()

// check generated compare content
String compare = compareFile.text

assert compare.contains( "version=1.0-SNAPSHOT" )
assert compare.contains( "ok=1" )
assert compare.contains( "ko=1" )
assert compare.contains( "ignored=1" )
assert compare.contains( 'okFiles="mono-1.0-SNAPSHOT.pom"' )
assert compare.contains( 'koFiles="mono-1.0-SNAPSHOT.jar"' )
assert compare.contains( 'ignoredFiles="mono-1.0-SNAPSHOT.spdx.json"' )
if( File.separator == '/' ) {
  assert compare.contains( '# diffoscope target/reference/org.apache.maven.plugins.it.compare/mono-1.0-SNAPSHOT.jar target/mono-1.0-SNAPSHOT.jar' )
} else {
  assert compare.contains( '# diffoscope target\\reference\\org.apache.maven.plugins.it.compare\\mono-1.0-SNAPSHOT.jar target\\mono-1.0-SNAPSHOT.jar' )
}

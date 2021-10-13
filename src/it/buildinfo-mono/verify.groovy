
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

// check existence of generated buildinfo in target
File buildinfoFile = new File( basedir, "target/mono-1.0-SNAPSHOT.buildinfo" );
assert buildinfoFile.isFile()

// check generated buildinfo content
String buildinfo = buildinfoFile.text

assert buildinfo.contains( "outputs.0.filename=mono-1.0-SNAPSHOT.pom" )
assert buildinfo.contains( "outputs.1.filename=mono-1.0-SNAPSHOT.jar" )
assert buildinfo.contains( "mvn.minimum.version=3.0.5" )

// check existence of buildinfo in local repository
File local = new File( basedir, "../../local-repo/org/apache/maven/plugins/it/mono/1.0-SNAPSHOT/mono-1.0-SNAPSHOT.buildinfo")
assert local.isFile()

// check existence of buildinfo in remote repository
File remoteDir = new File( basedir, "target/remote-repo/org/apache/maven/plugins/it/mono/1.0-SNAPSHOT")
  assert remoteDir.isDirectory()
int count = 0;
for ( File f : remoteDir.listFiles() )
{
  if ( f.getName().endsWith( ".pom" ) )
  {
    File b = new File( remoteDir, f.getName().replace( ".pom", ".buildinfo" ) )
    println b
    assert b.isFile()
    count++
  }
}
assert count > 0

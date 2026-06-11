
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

// check existence of generated aggregate buildinfo in target and root copy
File buildinfoFile = new File( basedir, "target/multi-1.0-SNAPSHOT.buildinfo" );
assert buildinfoFile.isFile()

File modA = new File( basedir, "modA/target/multi-modA-1.0-SNAPSHOT.buildinfo")
assert !modA.isFile()

File modB = new File( basedir, "modB/target/multi-modB-1.0-SNAPSHOT.buildinfo")
assert modB.isFile()

// check generated aggregate buildinfo content
String buildinfo = modB.text
assert buildinfo.contains( "group-id=org.apache.maven.plugins.it" )
assert buildinfo.contains( "artifact-id=multi" )
assert buildinfo.contains( "version=1.0-SNAPSHOT" )
assert buildinfo.contains( "outputs.1.coordinates=org.apache.maven.plugins.it:multi-modA" )
assert buildinfo.contains( "outputs.1.0.filename=multi-modA-1.0-SNAPSHOT.pom" )

if (mavenVersion.startsWith('4.') || mavenVersion.startsWith('3.10.')) {
  assert buildinfo.contains( "outputs.1.2.filename=multi-modA-1.0-SNAPSHOT.jar" )
} else {
  assert buildinfo.contains( "outputs.1.1.filename=multi-modA-1.0-SNAPSHOT.jar" )
}

assert buildinfo.contains( "outputs.2.coordinates=org.apache.maven.plugins.it:multi-modB" )
assert buildinfo.contains( "outputs.2.0.filename=multi-modB-1.0-SNAPSHOT.pom" )

if (mavenVersion.startsWith('4.') || mavenVersion.startsWith('3.10.')) {
  assert buildinfo.contains( "outputs.2.2.filename=multi-modB-1.0-SNAPSHOT.jar" )
} else {
  assert buildinfo.contains( "outputs.2.1.filename=multi-modB-1.0-SNAPSHOT.jar" )
}


assert !buildinfo.contains( ".buildinfo" )
assert !buildinfo.contains( "outputs.3" )

// check existence of buildinfo in local repository
File localModB = new File( basedir, "../../local-repo/org/apache/maven/plugins/it/multi-modB/1.0-SNAPSHOT/multi-modB-1.0-SNAPSHOT.buildinfo")
assert localModB.isFile()

// check existence of buildinfo in remote repository
File remoteDir = null
if (mavenVersion.startsWith('4.') || mavenVersion.startsWith('3.10.')) {
  remoteDir = new File( basedir, "target/remote-repo/org/apache/maven/plugins/it/multi-modB/1.0-SNAPSHOT" )
} else {
  remoteDir = new File( basedir, "modB/target/remote-repo/org/apache/maven/plugins/it/multi-modB/1.0-SNAPSHOT" )
}

assert remoteDir.isDirectory()

// check if there is a .buildinfo for the first .pom file
for ( File f : remoteDir.listFiles() )  {
  // In Maven 4 there is the build-POM and also the new (but with old name) consumer-POM
  // The expected ".buildinfo" file is named as the consumer-POM
  if ( f.getName().endsWith( ".pom" ) &&  !f.getName().endsWith( "build.pom" )) {
    File b = new File( remoteDir, f.getName().replace( ".pom", ".buildinfo" ) )
    println b
    assert b.isFile()
    return
  }
}

// issue: buildinfo not found
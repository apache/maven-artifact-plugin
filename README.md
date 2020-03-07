<!---
 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements.  See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->
Apache Maven Buildinfo Plugin Study
======================

[![Apache License, Version 2.0, January 2004](https://img.shields.io/github/license/apache/maven.svg?label=License)][license]

This plugin is a study, written as a Proof Of Concept of [Reproducible Builds](https://reproducible-builds.org/) tooling
to ease reproducing Maven builds that are [expected to be reproducible](https://maven.apache.org/guides/mini/guide-reproducible-builds.html): once [feedback](https://lists.apache.org/thread.html/ra05a971a2de961d27691bd4624850a06a862b4223116c0c904be8397%40%3Cdev.maven.apache.org%3E) will be given on Maven developper mailing list, we'll see if this will be moved
to a separate Maven plugin or if its unique goal will be merged to an existing plugin.

The purpose of this plugin is:

- to generate a buildinfo file from a build recording fingerprints of output files, as specified in [Reproducible Builds for the JVM](https://reproducible-builds.org/docs/jvm/)
  that will eventually be deployed to remote repository

- help rebuilders to check that they local build produces the same Reproducible Build output than the reference build
  published to a remote repository

To use this plugin, you'll need to build and install from source, or use SHAPSHOT from ```https://repository.apache.org/content/repositories/snapshots```

Generating buildinfo after a build
--------------

```
mvn verify buildinfo:save
```

Deploy to remote repository
--------------

Configure the plugin with its ```save```
goal in your ```pom.xml```

Check local build against remote reference
--------------

If reference build is available in a remote repository with predefined id, like ```central```:

```
mvn verify buildinfo:save -Dreference.repo=central
```

If reference build is available in a remote repository without predefined id, use its url instead:

```
mvn verify buildinfo:save -Dreference.repo=https://repository.apache.org/content/groups/maven-staging-group/
```

Available Reproducible Releases in Maven Central Repository 
--------------

- [org.apache.maven.plugins:maven-shade-plugin:3.2.2](https://repo.maven.apache.org/maven2/org/apache/maven/plugins/maven-shade-plugin/3.2.2/) mono-module, with source-release

- [org.apache;maven.doxia:doxia:1.9.1](https://repo.maven.apache.org/maven2/org/apache/maven/doxia/doxia/1.9.1/) multi-module, with source-release

- [info.guardianproject:jtorctl:0.4](https://repo.maven.apache.org/maven2/info/guardianproject/jtorctl/0.4/) mono-module with provided buildinfo

- [org.apache.sling:org.apache.sling.installer.core:3.10.2](https://repo.maven.apache.org/maven2/org/apache/sling/org.apache.sling.installer.core/3.10.2/) OSGI, with source-release

[license]: https://www.apache.org/licenses/LICENSE-2.0

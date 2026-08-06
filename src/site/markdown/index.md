<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# Apache Maven Artifact Plugin
The Artifact Plugin is used to manage artifacts tasks.

## Goals Overview

The Artifact Plugin has 4 goals currently:

- [artifact:buildinfo](./buildinfo-mojo.html) records current build results (from `package`) in [Reproducible Builds buildinfo](https://reproducible-builds.org/docs/jvm/) file,
- [artifact:compare](./compare-mojo.html) compares current build output (from `package`) against reference build previously published,
- [artifact:check-buildplan](./check-buildplan-mojo.html) checks the project's buildplan to find if any used [plugin has a known Reproducible Builds issue](./plugin-issues.html),
- [artifact:describe-build-output](./describe-build-output-mojo.html) (experimental) describes build structure and output,
- [artifact:reproducible-central](./reproducible-central-mojo.html) (experimental) report shows if dependencies are proven Reproducible Builds at Reproducible Central.
## Usage

General instructions on how to use the Artifact Plugin can be found on the [usage page](./usage.html). Some more specific use cases are described in the examples given below.

In case you still have questions regarding the plugin's usage, please have a look at the [FAQ](./faq.html) and feel free to contact the [user mailing list](./mailing-lists.html). The posts to the mailing list are archived and could already contain the answer to your question as part of an older thread. Hence, it is also worth browsing/searching the [mail archive](./mailing-lists.html).

If you feel like the plugin is missing a feature or has a defect, you can fill a feature request or bug report in our [issue tracker](./issue-management.html). When creating a new issue, please provide a comprehensive description of your concern. Especially for fixing bugs it is crucial that the developers can reproduce your problem. For this reason, entire debug logs, POMs or most preferably little demo projects attached to the issue are very much appreciated. Of course, patches are welcome, too. Contributors can check out the project from our [source repository](./scm.html) and will find supplementary information in the [guide to helping with Maven](http://maven.apache.org/guides/development/guide-helping.html).

## Examples

To provide you with better understanding on some usages of the Artifact Plugin, you can take a look into the following examples:

- [How to diagnose issues with Reproducible Builds?](./reproducible.html)

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

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.logging.MessageUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;

import static org.apache.maven.plugins.artifact.buildinfo.BuildInfoWriter.getArtifactFilename;

/**
 * Compare current build output (from {@code package}) against reference either previously {@code install}-ed or downloaded from a remote
 * repository: comparison results go to {@code .buildcompare} file.
 *
 * @since 3.2.0
 */
@Mojo(name = "compare", threadSafe = false)
public class CompareMojo extends AbstractBuildinfoMojo {
    /**
     * Repository for reference build, containing either reference buildinfo file or reference artifacts.<br/>
     * Format: <code>id</code> or <code>url</code> or <code>id::url</code>
     * <dl>
     * <dt>id</dt>
     * <dd>The repository id</dd>
     * <dt>url</dt>
     * <dd>The url of the repository</dd>
     * </dl>
     * @see <a href="https://maven.apache.org/ref/current/maven-model/maven.html#repository">repository definition</a>
     */
    @Parameter(property = "reference.repo", defaultValue = "central")
    private String referenceRepo;

    /**
     * Compare aggregate only (ie wait for the last module) or also compare on each module.
     * @since 3.2.0
     */
    @Parameter(property = "compare.aggregate.only", defaultValue = "false")
    private boolean aggregateOnly;

    /**
     * The entry point to Maven Artifact Resolver, i.e. the component doing all the work.
     */
    @Component
    private RepositorySystem repoSystem;

    /**
     * The current repository/network configuration of Maven.
     */
    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;

    /**
     * The project's remote repositories to use for the resolution.
     */
    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    private List<RemoteRepository> remoteRepos;

    /**
     * Fail the build if differences are found against reference build.
     * @since 3.5.0
     */
    @Parameter(property = "compare.fail", defaultValue = "true")
    private boolean fail;

    @Override
    void execute(Map<Artifact, String> artifacts) throws MojoExecutionException {
        getLog().info("Checking against reference build from " + referenceRepo + "...");
        checkAgainstReference(artifacts, session.getProjects().size() == 1);
    }

    @Override
    protected void skip(MavenProject last) throws MojoExecutionException {
        if (aggregateOnly) {
            return;
        }

        // try to download reference artifacts for current project and check if there are issues to give early feedback
        checkAgainstReference(generateBuildinfo(true), true);
    }

    /**
     * Check current build result with reference.
     *
     * @param artifacts a Map of artifacts added to the build info with their associated property key prefix
     *            (<code>outputs.[#module.].#artifact</code>)
     * @throws MojoExecutionException if anything goes wrong
     */
    private void checkAgainstReference(Map<Artifact, String> artifacts, boolean mono) throws MojoExecutionException {
        MavenProject root = mono ? project : getExecutionRoot();
        File referenceDir = new File(root.getBuild().getDirectory(), "reference");
        referenceDir.mkdirs();

        // download or create reference buildinfo
        File referenceBuildinfo = downloadOrCreateReferenceBuildinfo(mono, artifacts, referenceDir);

        // compare outputs from reference buildinfo vs actual
        compareWithReference(artifacts, referenceBuildinfo);
    }

    private File downloadOrCreateReferenceBuildinfo(boolean mono, Map<Artifact, String> artifacts, File referenceDir)
            throws MojoExecutionException {
        RemoteRepository repo = createReferenceRepo();

        ReferenceBuildinfoUtil rmb =
                new ReferenceBuildinfoUtil(getLog(), referenceDir, artifacts, repoSystem, repoSession, rtInformation);

        return rmb.downloadOrCreateReferenceBuildinfo(repo, project, buildinfoFile, mono);
    }

    private void compareWithReference(Map<Artifact, String> artifacts, File referenceBuildinfo)
            throws MojoExecutionException {
        Properties actual = BuildInfoWriter.loadOutputProperties(buildinfoFile);
        Properties reference = BuildInfoWriter.loadOutputProperties(referenceBuildinfo);

        int ok = 0;
        List<String> okFilenames = new ArrayList<>();
        List<String> koFilenames = new ArrayList<>();
        List<String> diffoscopes = new ArrayList<>();
        List<String> ignored = new ArrayList<>();
        File referenceDir = referenceBuildinfo.getParentFile();
        for (Map.Entry<Artifact, String> entry : artifacts.entrySet()) {
            Artifact artifact = entry.getKey();
            String prefix = entry.getValue();
            if (prefix == null) {
                // ignored file
                ignored.add(getArtifactFilename(artifact));
                continue;
            }

            String[] checkResult = checkArtifact(artifact, prefix, reference, actual, referenceDir);
            String filename = checkResult[0];
            String diffoscope = checkResult[1];

            if (diffoscope == null) {
                ok++;
                okFilenames.add(filename);
            } else {
                koFilenames.add(filename);
                diffoscopes.add(diffoscope);
            }
        }

        int ko = artifacts.size() - ok - ignored.size();
        int missing = reference.size() / 3 /* 3 property keys par file: filename, length and checksums.sha512 */;

        if (ko + missing > 0) {
            getLog().error("Reproducible Build output summary: "
                    + MessageUtils.buffer().success(ok + " files ok")
                    + ", " + MessageUtils.buffer().failure(ko + " different")
                    + ((missing == 0) ? "" : (", " + MessageUtils.buffer().failure(missing + " missing")))
                    + ((ignored.isEmpty()) ? "" : (", " + MessageUtils.buffer().warning(ignored.size() + " ignored"))));
            getLog().error("see "
                    + MessageUtils.buffer()
                            .project("diff " + relative(referenceBuildinfo) + " " + relative(buildinfoFile))
                            .build());
            getLog().error("see also https://maven.apache.org/guides/mini/guide-reproducible-builds.html");
        } else {
            getLog().info("Reproducible Build output summary: "
                    + MessageUtils.buffer().success(ok + " files ok")
                    + ((ignored.isEmpty()) ? "" : (", " + MessageUtils.buffer().warning(ignored.size() + " ignored"))));
        }

        // save .compare file
        File buildcompare = new File(
                buildinfoFile.getParentFile(), buildinfoFile.getName().replaceFirst(".buildinfo$", ".buildcompare"));
        try (PrintWriter p = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(Files.newOutputStream(buildcompare.toPath()), StandardCharsets.UTF_8)))) {
            p.println("version=" + project.getVersion());
            p.println("ok=" + ok);
            p.println("ko=" + ko);
            p.println("ignored=" + ignored.size());
            p.println("okFiles=\"" + String.join(" ", okFilenames) + '"');
            p.println("koFiles=\"" + String.join(" ", koFilenames) + '"');
            p.println("ignoredFiles=\"" + String.join(" ", ignored) + '"');
            Properties ref = new Properties();
            if (referenceBuildinfo != null) {
                try (InputStream in = Files.newInputStream(referenceBuildinfo.toPath())) {
                    ref.load(in);
                } catch (IOException e) {
                    // nothing
                }
            }
            String v = ref.getProperty("java.version");
            if (v != null) {
                p.println("reference_java_version=\"" + v + '"');
            }
            v = ref.getProperty("os.name");
            if (v != null) {
                p.println("reference_os_name=\"" + v + '"');
            }
            for (String diffoscope : diffoscopes) {
                p.print("# ");
                p.println(diffoscope);
            }
            getLog().info("Reproducible Build output comparison saved to " + buildcompare);
        } catch (IOException e) {
            throw new MojoExecutionException("Error creating file " + buildcompare, e);
        }

        copyAggregateToRoot(buildcompare);

        if (fail && (ko + missing > 0)) {
            throw new MojoExecutionException("Build artifacts are different from reference");
        }
    }

    // { filename, diffoscope }
    private String[] checkArtifact(
            Artifact artifact, String prefix, Properties reference, Properties actual, File referenceDir) {
        String actualFilename = (String) actual.remove(prefix + ".filename");
        String actualLength = (String) actual.remove(prefix + ".length");
        String actualSha512 = (String) actual.remove(prefix + ".checksums.sha512");

        String referencePrefix = findPrefix(reference, artifact.getGroupId(), actualFilename);
        String referenceLength = (String) reference.remove(referencePrefix + ".length");
        String referenceSha512 = (String) reference.remove(referencePrefix + ".checksums.sha512");
        reference.remove(referencePrefix + ".groupId");

        String issue = null;
        if (!actualLength.equals(referenceLength)) {
            issue = "size";
        } else if (!actualSha512.equals(referenceSha512)) {
            issue = "sha512";
        }

        if (issue != null) {
            String diffoscope = diffoscope(artifact, referenceDir);
            getLog().error(issue + " mismatch " + MessageUtils.buffer().strong(actualFilename) + ": investigate with "
                    + MessageUtils.buffer().project(diffoscope));
            return new String[] {actualFilename, diffoscope};
        }
        return new String[] {actualFilename, null};
    }

    private String diffoscope(Artifact a, File referenceDir) {
        File actual = a.getFile();
        // notice: actual file name may have been defined in pom
        // reference file name is taken from repository format
        File reference = new File(new File(referenceDir, a.getGroupId()), getRepositoryFilename(a));
        if (actual == null) {
            return "missing file for " + ArtifactIdUtils.toId(a) + " reference = " + relative(reference)
                    + " actual = null";
        }
        return "diffoscope " + relative(reference) + " " + relative(actual);
    }

    private String getRepositoryFilename(Artifact a) {
        String path = session.getRepositorySession().getLocalRepositoryManager().getPathForLocalArtifact(a);
        return path.substring(path.lastIndexOf('/'));
    }

    private String relative(File file) {
        File basedir = getExecutionRoot().getBasedir();
        int length = basedir.getPath().length();
        String path = file.getPath();
        return path.substring(length + 1);
    }

    private static String findPrefix(Properties reference, String actualGroupId, String actualFilename) {
        for (String name : reference.stringPropertyNames()) {
            if (name.endsWith(".filename") && actualFilename.equals(reference.getProperty(name))) {
                String prefix = name.substring(0, name.length() - ".filename".length());
                if (actualGroupId.equals(reference.getProperty(prefix + ".groupId"))) {
                    reference.remove(name);
                    return prefix;
                }
            }
        }
        return null;
    }

    private RemoteRepository createReferenceRepo() throws MojoExecutionException {
        if (referenceRepo.contains("::")) {
            // id::url
            int index = referenceRepo.indexOf("::");
            String id = referenceRepo.substring(0, index);
            String url = referenceRepo.substring(index + 2);
            return createDeploymentArtifactRepository(id, url);
        } else if (referenceRepo.contains(":")) {
            // url, will use default "reference" id
            return createDeploymentArtifactRepository("reference", referenceRepo);
        }

        // id
        for (RemoteRepository repo : remoteRepos) {
            if (referenceRepo.equals(repo.getId())) {
                return repo;
            }
        }
        throw new MojoExecutionException("Could not find repository with id = " + referenceRepo);
    }

    private static RemoteRepository createDeploymentArtifactRepository(String id, String url) {
        return new RemoteRepository.Builder(id, "default", url).build();
    }
}

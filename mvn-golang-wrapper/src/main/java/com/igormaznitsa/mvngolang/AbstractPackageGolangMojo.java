/*
 * Copyright 2016 Igor Maznitsa.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.igormaznitsa.mvngolang;

import com.igormaznitsa.meta.annotation.MustNotContainNull;
import com.igormaznitsa.meta.common.utils.ArrayUtils;
import com.igormaznitsa.meta.common.utils.GetUtils;
import com.igormaznitsa.mvngolang.utils.IOUtils;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.maven.plugins.annotations.Parameter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.io.FilenameUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResult;
import org.zeroturnaround.zip.ZipUtil;

public abstract class AbstractPackageGolangMojo extends AbstractGolangMojo {

  @Component
  private ArtifactResolver artifactResolver;

  @Parameter(defaultValue = "${project.remoteArtifactRepositories}", readonly = true, required = true)
  private List<ArtifactRepository> remoteRepositories;

  /**
   * List of packages.
   */
  @Parameter(name = "packages")
  private String[] packages;

  /**
   * Find artifacts generated by Mvn-Golang among scope dependencies, unpack them and
   * add unpacked folders into GOPATHduring execution.
   *
   * @since 2.2.1
   */
  @Parameter(name = "scanDependencies", defaultValue = "true")
  private boolean scanDependencies = true;

  @Nullable
  @MustNotContainNull
  protected String[] getDefaultPackages() {
    return null;
  }

  @Nullable
  @MustNotContainNull
  public String[] getPackages() {
    return this.packages == null ? this.getDefaultPackages() : this.packages.clone();
  }

  /**
   * Internal variable to keep GOPATH part containing folders of unpacked
   * mvn-golang dependencies.
   *
   * @since 2.2.1
   */
  private String extraGoPathSectionInOsFormat = "";

  public boolean isScanDependencies() {
    return this.scanDependencies;
  }

  public void setScanDependencies(final boolean flag) {
    this.scanDependencies = flag;
  }

  public void setPackages(@Nullable @MustNotContainNull final String[] value) {
    this.packages = value;
  }

  @Override
  @Nonnull
  @MustNotContainNull
  public String[] getTailArguments() {
    return GetUtils.ensureNonNull(getPackages(), ArrayUtils.EMPTY_STRING_ARRAY);
  }

  @Override
  @Nonnull
  @MustNotContainNull
  public String[] getCommandFlags() {
    return ArrayUtils.EMPTY_STRING_ARRAY;
  }

  @Nonnull
  @Override
  protected final String getSpecialPartOfGoPath() {
    return this.extraGoPathSectionInOsFormat;
  }

  @Nonnull
  private ProjectBuildingRequest newResolveArtifactProjectBuildingRequest() {
    final ProjectBuildingRequest result = new DefaultProjectBuildingRequest(this.getSession().getProjectBuildingRequest());
    result.setRemoteRepositories(this.remoteRepositories);
    return result;
  }

  @Override
  public final void doPrepare() throws MojoFailureException, MojoExecutionException {
    if (this.isScanDependencies()) {
      final Set<File> foundArtifacts = this.scanForMvnGoArtifacts();
      getLog().debug("Found mvn-golang artifactis: " + foundArtifacts);
      final File targetFolder = new File(this.getProject().getBuild().getDirectory(), "__dependencies__");
      getLog().debug("Depedencies will be unpacked into folder: " + targetFolder);
      final List<File> unpackedFolders = unpackArtifactsIntoFolder(foundArtifacts, targetFolder);

      final String preparedExtraPartForGoPath = IOUtils.makeOsFilePathWithoutDuplications(unpackedFolders.toArray(new File[0]));
      getLog().debug("Prepared dependency path for GOPATH: " + preparedExtraPartForGoPath);
      this.extraGoPathSectionInOsFormat = preparedExtraPartForGoPath;
    }
  }

  @Nonnull
  @MustNotContainNull
  private List<File> unpackArtifactsIntoFolder(@Nonnull @MustNotContainNull final Set<File> zippedArtifacts, @Nonnull final File targetFolder) throws MojoExecutionException {
    final List<File> resultFolders = new ArrayList<>();

    if (!targetFolder.isDirectory() && !targetFolder.mkdirs()) {
      throw new MojoExecutionException("Can't create folder to unpack dependencies: " + targetFolder);
    }

    for (final File zipFile : zippedArtifacts) {
      final File outDir = new File(targetFolder, FilenameUtils.getBaseName(zipFile.getName()));
      if (outDir.isDirectory()) {
        getLog().debug("Dependency already unpacked: " + outDir);
        resultFolders.add(outDir);
      } else {
        try {
          getLog().debug("Unpack dependency archive: " + zipFile);
          ZipUtil.unpack(zipFile, outDir, StandardCharsets.UTF_8);
          resultFolders.add(outDir);
        } catch (Exception ex) {
          throw new MojoExecutionException("Can't unpack dependency archive '" + zipFile.getName() + "' into folder '" + targetFolder + '\'', ex);
        }
      }
    }
    return resultFolders;
  }

  protected boolean isTestPhase() {
    final String phase = this.getExecution().getLifecyclePhase();
    return phase != null && (phase.equals("test") || phase.equals("process-test-resources") || phase.equals("test-compile"));
  }

  @Nonnull
  @MustNotContainNull
  private Set<File> scanForMvnGoArtifacts() throws MojoFailureException {
    final Set<File> result = new HashSet<>();
    final String phase = this.getExecution().getLifecyclePhase();

    MavenProject currentProject = this.getProject();
    while (currentProject != null && !Thread.currentThread().isInterrupted()) {
      final Set<Artifact> dependencyArtifacts = currentProject.getDependencyArtifacts();
      getLog().debug("Detected dependency artifacts: " + dependencyArtifacts);

      if (dependencyArtifacts != null) {
        for (final Artifact artifact : dependencyArtifacts) {
          if (Artifact.SCOPE_TEST.equals(artifact.getScope()) && !isTestPhase()) {
            continue;
          }
          
          if (artifact.getType().equals("zip")) {
            try {
              final ArtifactResult artifactResult = this.artifactResolver.resolveArtifact(newResolveArtifactProjectBuildingRequest(), artifact);
              final File zipFillePath = artifactResult.getArtifact().getFile();

              if (ZipUtil.containsEntry(zipFillePath, GolangMvnInstallMojo.ARTIFACT_FLAG_FILE)) {
                getLog().debug("Detected MVN-GOLANG flag inside ZIP dependency: " + artifact.getGroupId() + ':' + artifact.getArtifactId() + ':' + artifact.getVersion() + ':' + artifact.getType());
                result.add(artifactResult.getArtifact().getFile());
              } else {
                getLog().warn("Detected ZIP dependency but there is not mvn-golang flag inside archive: " + artifact.getGroupId()+':'+artifact.getArtifactId()+':'+artifact.getVersion());
              }
            } catch (ArtifactResolverException ex) {
              throw new MojoFailureException("Can't resolve GoLang artifact: " + artifact, ex);
            }
          }
        }
      }
      currentProject = currentProject.hasParent() ? currentProject.getParent() : null;
    }

    return result;
  }
}

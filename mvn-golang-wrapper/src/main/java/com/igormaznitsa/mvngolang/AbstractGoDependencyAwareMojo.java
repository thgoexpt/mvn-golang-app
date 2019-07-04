/*
 * Copyright 2019 Igor Maznitsa.
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
import static com.igormaznitsa.meta.common.utils.Assertions.assertNotNull;
import com.igormaznitsa.mvngolang.utils.IOUtils;
import com.igormaznitsa.mvngolang.utils.MavenUtils;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;
import org.zeroturnaround.zip.NameMapper;
import org.zeroturnaround.zip.ZipUtil;

public abstract class AbstractGoDependencyAwareMojo extends AbstractGolangMojo {

  /**
   * Internal variable to keep GOPATH part containing folders of unpacked
   * mvn-golang dependencies.
   *
   * @since 2.3.0
   */
  private String extraGoPathSectionInOsFormat = "";

  /**
   * Find artifacts generated by Mvn-Golang among scope dependencies, unpack
   * them and add unpacked folders into GOPATHduring execution.
   *
   * @since 2.3.0
   */
  @Parameter(name = "scanDependencies", defaultValue = "true")
  private boolean scanDependencies = true;

  /**
   * Include test dependencies into scanning process activated if
   * {@code scanDependencies=true}
   *
   * @since 2.3.0
   */
  @Parameter(name = "includeTestDependencies", defaultValue = "true")
  private boolean includeTestDependencies = true;

  /**
   * Path to the folder where resolved mvn-golang dependency artifacts will be
   * temporary unpacked and those paths will be added into GOPATH, activated if
   * {@code scanDependencies=true}
   *
   * @since 2.3.0
   */
  @Parameter(name = "dependencyTempFolder", defaultValue = "${project.build.directory}${file.separator}.__deps__")
  private String dependencyTempFolder;

  @Nonnull
  public String getDependencyTempFolder() {
    return this.dependencyTempFolder;
  }

  public void setDependencyTempFolder(@Nonnull final String path) {
    this.dependencyTempFolder = assertNotNull(path);
  }

  public boolean isScanDependencies() {
    return this.scanDependencies;
  }

  public void setScanDependencies(final boolean flag) {
    this.scanDependencies = flag;
  }

  public boolean isIncludeTestDependencies() {
    return this.includeTestDependencies;
  }

  public void setIncludeTestDependencies(final boolean value) {
    this.includeTestDependencies = value;
  }

  @Override
  public final void doInit() throws MojoFailureException, MojoExecutionException {
    super.doInit();
    if (this.isScanDependencies()) {
      getLog().info("Scanning maven dependencies");
      final List<File> foundArtifacts;

      try {
        foundArtifacts = MavenUtils.scanForMvnGoArtifacts(
                this.getProject(),
                this.isIncludeTestDependencies(),
                this,
                this.getSession(),
                this.getExecution(),
                this.getArtifactResolver(),
                this.getRemoteRepositories());
      } catch (ArtifactResolverException ex) {
        throw new MojoFailureException("Can't resolve artifact", ex);
      }

      if (foundArtifacts.isEmpty()) {
        getLog().debug("Mvn golang dependencies are not found");
        this.extraGoPathSectionInOsFormat = "";
      } else {
        getLog().debug("Found mvn-golang artifactis: " + foundArtifacts);
        final File dependencyTempTargetFolder = new File(this.getDependencyTempFolder());
        getLog().debug("Depedencies will be unpacked into folder: " + dependencyTempTargetFolder);
        final List<File> unpackedFolders = unpackArtifactsIntoFolder(foundArtifacts, dependencyTempTargetFolder);

        final String preparedExtraPartForGoPath = IOUtils.makeOsFilePathWithoutDuplications(unpackedFolders.toArray(new File[0]));
        getLog().debug("Prepared dependency path for GOPATH: " + preparedExtraPartForGoPath);
        this.extraGoPathSectionInOsFormat = preparedExtraPartForGoPath;
      }
    } else {
      getLog().info("Maven dependency scanning is off");
    }
  }

  @Nonnull
  @MustNotContainNull
  private List<File> unpackArtifactsIntoFolder(@Nonnull @MustNotContainNull final List<File> zippedArtifacts, @Nonnull final File targetFolder) throws MojoExecutionException {
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
        if (ZipUtil.containsEntry(zipFile, GolangMvnInstallMojo.MVNGOLANG_BUILD_FOLDERS_FILE)) {
          final File srcTargetFolder = new File(outDir, "src");
          try {
            unzipSrcFoldersContent(zipFile, srcTargetFolder);
            resultFolders.add(outDir);
          } catch (Exception ex) {
            throw new MojoExecutionException("Can't unpack source folders from dependency archive '" + zipFile.getName() + "' into folder '" + srcTargetFolder + '\'', ex);
          }
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
    }
    return resultFolders;
  }

  private boolean unzipSrcFoldersContent(@Nonnull final File artifactZip, @Nonnull final File targetFolder) {
    final byte[] buildFolderListFile = ZipUtil.unpackEntry(artifactZip, GolangMvnInstallMojo.MVNGOLANG_BUILD_FOLDERS_FILE);
    if (buildFolderListFile == null) {
      return false;
    } else {
      final List<String> folderList = new ArrayList<>();
      for (final String folder : new String(buildFolderListFile, StandardCharsets.UTF_8).split("\\n")) {
        final String trimmed = folder.trim();
        if (trimmed.isEmpty()) {
          continue;
        }
        folderList.add(folder + '/');
      }

      for (final String folder : folderList) {
        ZipUtil.unpack(artifactZip, targetFolder, new NameMapper() {
          @Override
          @Nullable
          public String map(@Nonnull final String name) {
            if (name.startsWith(folder)) {
              return name.substring(folder.length());
            }
            return null;
          }
        });
      }
      return true;
    }
  }

  @Nonnull
  @Override
  protected final String getSpecialPartOfGoPath() {
    return this.extraGoPathSectionInOsFormat;
  }

}

package com.igormaznitsa.mvngolang;

import javax.annotation.Nonnull;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import com.igormaznitsa.meta.annotation.MustNotContainNull;
import com.igormaznitsa.meta.common.utils.ArrayUtils;
import com.igormaznitsa.meta.common.utils.GetUtils;

/**
 * The Mojo wraps the 'get' command.
 */
@Mojo(name = "get", defaultPhase = LifecyclePhase.INITIALIZE, threadSafe = true, requiresDependencyResolution = ResolutionScope.NONE)
public class GetMojo extends AbstractGolangMojo {

  /**
   * List of packages to be built.
   */
  @Parameter(name = "packages")
  private String[] packages;

  @Override
  @Nonnull
  @MustNotContainNull
  public String[] getCLITailArgs() {
    return GetUtils.ensureNonNull(this.packages, ArrayUtils.EMPTY_STRING_ARRAY);
  }
  
  
  @Override
  @Nonnull
  @MustNotContainNull
  public String[] getCommandLine() {
    return new String[]{"go","get"};
  }

  @Override
  public boolean enforcePrintOutput() {
    return true;
  }
}

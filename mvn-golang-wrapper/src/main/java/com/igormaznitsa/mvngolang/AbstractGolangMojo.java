package com.igormaznitsa.mvngolang;

import static com.igormaznitsa.meta.common.utils.Assertions.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import com.igormaznitsa.meta.annotation.LazyInited;
import com.igormaznitsa.meta.annotation.MustNotContainNull;
import com.igormaznitsa.meta.common.utils.ArrayUtils;
import com.igormaznitsa.meta.common.utils.GetUtils;
import com.igormaznitsa.mvngolang.utils.UnpackUtils;

public abstract class AbstractGolangMojo extends AbstractMojo {

  public static final String SDK_BASE_URL = "https://storage.googleapis.com/golang/";

  private static final List<String> ALLOWED_SDKARCHIVE_CONTENT_TYPE = Collections.unmodifiableList(Arrays.asList("application/octet-stream", "application/zip", "application/x-tar"));

  private static final ReentrantLock LOCKER = new ReentrantLock();

  private static final String[] BANNER = new String[]{"______  ___             _________     ______",
    "___   |/  /__   __________  ____/________  / ______ ______________ _",
    "__  /|_/ /__ | / /_  __ \\  / __ _  __ \\_  /  _  __ `/_  __ \\_  __ `/",
    "_  /  / / __ |/ /_  / / / /_/ / / /_/ /  /___/ /_/ /_  / / /  /_/ / ",
    "/_/  /_/  _____/ /_/ /_/\\____/  \\____//_____/\\__,_/ /_/ /_/_\\__, /",
    "                                                           /____/",
    "                  https://github.com/raydac/mvnGoLang",
    ""
  };

  /**
   * VERSION, OS, PLATFORM,-OSXVERSION
   */
  public static final String NAME_PATTERN = "go%s.%s-%s%s";

  /**
   * Hide ASC banner.
   */
  @Parameter(defaultValue = "false", name = "hideBanner")
  private boolean hideBanner;

  /**
   * Folder to be used to save and unpack loaded SDKs and also keep different info.
   */
  @Parameter(defaultValue = "${user.home}${file.separator}.mvnGoLang", name = "storeFolder")
  private String storeFolder;

  /**
   * Folder to be used as GOPATH. NB! The Environment variable also will be checked and if environment variable detected then value of it will be used.
   */
  @Parameter(defaultValue = "${user.home}${file.separator}.mvnGoLang${file.separator}.go_path", name = "goPath")
  private String goPath;

  /**
   * The Go SDK version. It plays role if goRoot is undefined.
   */
  @Parameter(name = "goVersion", defaultValue = "1.6", required = true)
  private String goVersion;

  /**
   * The Go home folder. It can be undefined and in the case the plug-in will make automatic business to find SDK in its cache or download it.
   */
  @Parameter(name = "goRoot")
  private String goRoot;

  /**
   * The Go bootstrap home.
   */
  @Parameter(name = "goRootBootstrap")
  private String goRootBootstrap;

  /**
   * Disable loading GoLang SDK through network if it is not found at cache.
   */
  @Parameter(name = "disableSdkLoad", defaultValue = "false")
  private boolean disableSdkLoad;

  /**
   * GoLang source directory.
   */
  @Parameter(defaultValue = "${basedir}${file.separator}src${file.separator}golang", name = "sources")
  private String sources;

  /**
   * The Target OS.
   */
  @Parameter(name = "targetOs")
  private String targetOs;

  /**
   * The OS. If it is not defined then plug-in will try figure out the current one.
   */
  @Parameter(name = "os")
  private String os;

  /**
   * The Target architecture.
   */
  @Parameter(name = "targetArch")
  private String targetArch;

  /**
   * The Architecture. If it is not defined then plug-in will try figure out the current one.
   */
  @Parameter(name = "arch")
  private String arch;

  /**
   * Version of OSX to be used during distributive name synthesis.
   */
  @Parameter(name = "osxVersion")
  private String osxVersion;

  /**
   * List of optional build flags.
   */
  @Parameter(name = "buildFlags")
  private String[] buildFlags;

  /**
   * Be verbose in logging.
   */
  @Parameter(name = "verbose", defaultValue = "false")
  private boolean verbose;

  /**
   * Do not delete SDK archive after unpacking.
   */
  @Parameter(name = "keepSdkArchive", defaultValue = "false")
  private boolean keepSdkArchive;

  /**
   * Name of tool to be called instead of standard 'go' tool.
   */
  @Parameter(name = "useGoTool")
  private String useGoTool;

  /**
   * Allows directly define the name of the SDK archive.
   */
  @Parameter(name = "sdkArchiveName")
  private String sdkArchiveName;

  /**
   * Disable search and usage of environment variables $GOROOT, $GOROOT_BOOTSTRAP, $GOOS, $GOARCH, $GOPATH which will be used if there is not directly defined info.
   */
  @Parameter(name = "dontUseEnvVars", defaultValue = "false")
  private boolean dontUseEnvVars;

  /**
   * It allows to define key value pairs which will be used as environment variables for started GoLang process.
   */
  @Parameter(name = "env")
  private Properties env;

  /**
   * Keep unpacked wrongly SDK folder.
   */
  @Parameter(name = "keepUnarchFolderIfError", defaultValue = "false")
  private boolean keepUnarchFolderIfError;

  @Nonnull
  public Properties getEnv() {
    return GetUtils.ensureNonNull(this.env, new Properties());
  }

  public boolean isDontUseEnvVars() {
    return this.dontUseEnvVars;
  }

  public boolean isKeepSdkArchive() {
    return this.keepSdkArchive;
  }

  public boolean isKeepUnarchFolderIfError() {
    return this.keepUnarchFolderIfError;
  }

  @Nullable
  public String getSdkArchiveName() {
    return this.sdkArchiveName;
  }

  @Nonnull
  public String getStoreFolder() {
    return this.storeFolder;
  }

  @Nullable
  public String getUseGoTool() {
    return this.useGoTool;
  }

  public boolean isVerbose() {
    return this.verbose;
  }

  public boolean isDisableSdkLoad() {
    return this.disableSdkLoad;
  }

  @Nonnull
  @MustNotContainNull
  public String[] getBuildFlags() {
    return GetUtils.ensureNonNull(this.buildFlags, ArrayUtils.EMPTY_STRING_ARRAY);
  }

  @Nonnull
  public File findGoPath(final boolean ensureExist) throws IOException {
    final String theGoPath = getGoPath();
    final File result = new File(theGoPath);
    if (ensureExist && !result.isDirectory() && !result.mkdirs()) {
      throw new IOException("Can't create folder : " + theGoPath);
    }
    return result;
  }

  @Nullable
  public File findGoRootBootstrap(final boolean ensureExist) throws IOException {
    final String value = getGoRootBootstrap();
    File result = null;
    if (value != null) {
      result = new File(value);
      if (ensureExist && !result.isDirectory()) {
        throw new IOException("Can't find folder for GOROOT_BOOTSTRAP: " + result);
      }
    }
    return result;
  }

  @Nonnull
  public String getOs() {
    String result = this.os;
    if (result == null) {
      if (SystemUtils.IS_OS_WINDOWS) {
        result = "windows";
      } else if (SystemUtils.IS_OS_LINUX) {
        result = "linux";
      } else if (SystemUtils.IS_OS_FREE_BSD) {
        result = "freebsd";
      } else {
        result = "darwin";
      }
    }
    return result;
  }

  @Nonnull
  public String getArch() {
    String result = this.arch;
    if (result == null) {
      result = investigateArch();
    }
    return result;
  }

  @Nonnull
  public String getGoPath() {
    final String foundInEnvironment = System.getenv("GOPATH");
    String result = assertNotNull(this.goPath);

    if (foundInEnvironment != null && !isDontUseEnvVars()){
      result = foundInEnvironment;
    }
    return result;
  }

  @Nullable
  public String getTargetOS() {
    String result = this.targetOs;
    if (result == null && !isDontUseEnvVars()) {
      result = System.getenv("GOOS");
    }
    return result;
  }

  @Nullable
  public String getTargetArch() {
    String result = this.targetArch;
    if (result == null && !isDontUseEnvVars()) {
      result = System.getenv("GOARCH");
    }
    return result;
  }

  @Nullable
  public String getOSXVersion() {
    String result = this.osxVersion;
    if (result == null && SystemUtils.IS_OS_MAC_OSX) {
      result = "osx10.6";
    }
    return result;
  }

  @Nonnull
  public String getGoVersion() {
    return this.goVersion;
  }

  @Nullable
  public String getGoRoot() {
    String result = this.goRoot;

    if (result == null && !isDontUseEnvVars()) {
      result = System.getenv("GOROOT");
    }

    return result;
  }

  @Nullable
  public String getGoRootBootstrap() {
    String result = this.goRootBootstrap;

    if (result == null && !isDontUseEnvVars()) {
      result = System.getenv("GOROOT_BOOTSTRAP");
    }

    return result;
  }

  @Nonnull
  public File getSources(final boolean ensureExist) throws IOException {
    final File result = new File(this.sources);
    if (ensureExist && !result.isDirectory()) {
      throw new IOException("Can't find GoLang project sources : " + result);
    }
    return result;
  }

  @LazyInited
  private HttpClient httpClient;

  @Nonnull
  private synchronized HttpClient getHttpClient() {
    if (this.httpClient == null) {
      this.httpClient = new HttpClient();
    }
    return this.httpClient;
  }

  @Nonnull
  private String loadGoLangSdkList() throws IOException {
    getLog().warn("Loading list of available GoLang SDKs from " + SDK_BASE_URL);

    final GetMethod get = new GetMethod(SDK_BASE_URL);
    get.setRequestHeader("Accept", "application/xml");
    try {
      final int status = getHttpClient().executeMethod(get);
      if (status == HttpStatus.SC_OK) {
        final String content = get.getResponseBodyAsString();
        getLog().info("GoLang SDK list has been loaded successfuly");
        getLog().debug(content);
        return content;
      } else {
        throw new IOException("Can't load list of allowed SDKs, status code is " + status);
      }
    } finally {
      get.releaseConnection();
    }
  }

  @Nonnull
  private Document convertSdkListToDocument(@Nonnull final String sdkListAsString) throws IOException {
    try {
      final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      final DocumentBuilder builder = factory.newDocumentBuilder();
      return builder.parse(new InputSource(new StringReader(sdkListAsString)));
    } catch (ParserConfigurationException ex) {
      getLog().error("Can't configure XML parser", ex);
      throw new IOException("Can't configure XML parser", ex);
    } catch (SAXException ex) {
      getLog().error("Can't parse document", ex);
      throw new IOException("Can't parse document", ex);
    } catch (IOException ex) {
      getLog().error("Unexpected IOException", ex);
      throw new IOException("Unexpected IOException", ex);
    }
  }

  private ByteArrayOutputStream consoleErrBuffer;
  private ByteArrayOutputStream consoleOutBuffer;

  private static void deleteFileIfExists(@Nonnull final File file) throws IOException {
    if (file.isFile() && !file.delete()) {
      throw new IOException("Can't delete file : " + file);
    }
  }

  protected void logOptionally(@Nonnull final String message) {
    if (getLog().isDebugEnabled() || this.verbose) {
      getLog().info(message);
    }
  }

  private void initConsoleBuffers() {
    this.consoleErrBuffer = new ByteArrayOutputStream();
    this.consoleOutBuffer = new ByteArrayOutputStream();
  }

  @Nonnull
  private static String extractArchiveName(@Nonnull final ArchiveInputStream reader) {
    final String name = reader.getClass().getSimpleName();
    final String basename = ArchiveInputStream.class.getSimpleName().toLowerCase(Locale.ENGLISH);
    final int index = name.toLowerCase(Locale.ENGLISH).indexOf(basename);
    return index < 0 ? name : name.substring(0, index);
  }

  @Nonnull
  private File unpackArchToFolder(@Nonnull final File archiveFile, @Nonnull final String folderInArchive, @Nonnull final File destinationFolder) throws IOException {
    getLog().info(String.format("Unpacking archive %s to folder %s", archiveFile.getName(), destinationFolder.getName()));

    boolean detectedError = true;
    try {

      final int unpackedFileCounter = UnpackUtils.unpackFileToFolder(folderInArchive, archiveFile, destinationFolder, true);
      if (unpackedFileCounter == 0) {
        throw new IOException("Couldn't find folder '" + folderInArchive + "' in archive or the archive is empty");
      } else {
        getLog().info("Unpacked " + unpackedFileCounter + " file(s)");
      }

      detectedError = false;

    } finally {
      if (detectedError && !isKeepUnarchFolderIfError()) {
        logOptionally("Deleting folder because error during unpack : " + destinationFolder);
        FileUtils.deleteQuietly(destinationFolder);
      }
    }
    return destinationFolder;
  }

  @Nonnull
  private File loadSDKAndUnpackIntoCache(@Nonnull final File cacheFolder, @Nonnull final String baseSdkName, @Nonnull final String sdkFileName) throws IOException {
    final File archiveFile = new File(cacheFolder, sdkFileName);
    final File sdkFolder = new File(cacheFolder, baseSdkName);

    final String sdkUrl = SDK_BASE_URL + sdkFileName;

    final GetMethod methodGet = new GetMethod(sdkUrl);
    methodGet.setFollowRedirects(true);

    boolean errorsDuringLoading = true;

    try {
      if (!archiveFile.isFile()) {
        getLog().warn("Loading SDK archive with URL : " + sdkUrl);

        final int status = getHttpClient().executeMethod(methodGet);
        if (status != HttpStatus.SC_OK) {
          throw new IOException("Can't load SDK archive for URL : " + sdkUrl + " [" + status + ']');
        }
        final String contentType = methodGet.getResponseHeader("Content-Type").getValue();

        if (!ALLOWED_SDKARCHIVE_CONTENT_TYPE.contains(contentType)) {
          throw new IOException("Unsupported content type : " + contentType);
        }

        final InputStream inStream = methodGet.getResponseBodyAsStream();
        FileUtils.copyInputStreamToFile(inStream, archiveFile);

        getLog().info("Archived SDK has been succesfully downloaded, its size is " + (archiveFile.length() / 1024L) + " Kb");

        inStream.close();
      } else {
        getLog().info("Archive file of SDK has been found in the cache : " + archiveFile);
      }

      errorsDuringLoading = false;

      return unpackArchToFolder(archiveFile, "go", sdkFolder);
    } finally {
      methodGet.releaseConnection();
      if (errorsDuringLoading || !this.isKeepSdkArchive()) {
        logOptionally("Deleting archive : " + archiveFile + (errorsDuringLoading ? " (because error during loading)" : ""));
        deleteFileIfExists(archiveFile);
      } else {
        logOptionally("Archive file is kept : " + archiveFile);
      }
    }
  }

  @Nonnull
  private String extractSDKFileName(@Nonnull final Document doc, @Nonnull final String sdkBaseName, @Nonnull @MustNotContainNull final String[] allowedExtensions) throws IOException {
    getLog().debug("Looking for SDK started with base name : " + sdkBaseName);

    final Set<String> variants = new HashSet<String>();
    for (final String ext : allowedExtensions) {
      variants.add(sdkBaseName + '.' + ext);
    }

    final List<String> listedSdk = new ArrayList<String>();

    final Element root = doc.getDocumentElement();
    if ("ListBucketResult".equals(root.getTagName())) {
      final NodeList list = root.getElementsByTagName("Contents");
      for (int i = 0; i < list.getLength(); i++) {
        final Element element = (Element) list.item(i);
        final NodeList keys = element.getElementsByTagName("Key");
        if (keys.getLength() > 0) {
          final String text = keys.item(0).getTextContent();
          if (variants.contains(text)) {
            logOptionally("Detected compatible SDK in the SDK list : " + text);
            return text;
          } else {
            listedSdk.add(text);
          }
        }
      }

      getLog().error("Can't find any SDK to be used as " + sdkBaseName);
      getLog().error("GoLang list contains listed SDKs");
      getLog().error("..................................................");
      for (final String s : listedSdk) {
        getLog().error(s);
      }

      throw new IOException("Can't find SDK : " + sdkBaseName);
    } else {
      throw new IOException("It is not a ListBucket file [" + root.getTagName() + ']');
    }
  }

  @Nonnull
  private File findGoRoot() throws IOException, MojoFailureException {
    LOCKER.lock();
    try {
      final String definedGoRoot = this.getGoRoot();

      if (definedGoRoot == null) {
        final File cacheFolder = new File(this.storeFolder);

        if (!cacheFolder.isDirectory()) {
          logOptionally("Making SDK cache folder : " + cacheFolder);
          if (!cacheFolder.mkdirs()) {
            throw new IOException("Can't create folder " + cacheFolder);
          }
        }

        final String osx = getOSXVersion();

        final String sdkBaseName = String.format(NAME_PATTERN,
            this.getGoVersion().toLowerCase(Locale.ENGLISH),
            this.getOs().toLowerCase(Locale.ENGLISH),
            this.getArch().toLowerCase(Locale.ENGLISH),
            osx == null || !SystemUtils.IS_OS_MAC_OSX ? "" : "-" + osx.toLowerCase(Locale.ENGLISH));

        final File alreadyCached = new File(cacheFolder, sdkBaseName);

        if (alreadyCached.isDirectory()) {
          logOptionally("Cached SDK detected : " + alreadyCached);
          return alreadyCached;
        } else {
          if (this.disableSdkLoad) {
            throw new MojoFailureException("Can't find " + sdkBaseName + " in the cache but loading is directly disabled");
          }
          final Document parsed = convertSdkListToDocument(loadGoLangSdkList());
          final String predefinedArchive = this.getSdkArchiveName();
          if (predefinedArchive != null) {
            getLog().warn("Detected predefined archive name : " + predefinedArchive);
          }
          return loadSDKAndUnpackIntoCache(cacheFolder, sdkBaseName, predefinedArchive == null ? extractSDKFileName(parsed, sdkBaseName, new String[]{"tar.gz", "zip"}) : predefinedArchive);
        }
      } else {
        return new File(definedGoRoot);
      }
    } finally {
      LOCKER.unlock();
    }
  }

  @Nonnull
  private static String investigateArch() {
    final String arch = System.getProperty("os.arch").toLowerCase(Locale.ENGLISH);
    if (arch.contains("arm")) {
      return "arm";
    }
    if (arch.equals("386") || arch.equals("i386") || arch.equals("x86")) {
      return "386";
    }
    return "amd64";
  }

  private void printBanner() {
    for (final String s : BANNER) {
      getLog().info(s);
    }
  }

  public boolean isHideBanner() {
    return this.hideBanner;
  }

  protected boolean doesNeedOneMoreAttempt(@Nonnull final ProcessResult result, @Nonnull final String consoleOut, @Nonnull final String consoleErr) throws IOException, MojoExecutionException {
    return false;
  }

  @Override
  public final void execute() throws MojoExecutionException, MojoFailureException {

    if (!isHideBanner()) {
      printBanner();
    }

    beforeExecution();

    boolean error = false;
    try {
      int iterations = 0;

      while (true) {
        final ProcessExecutor executor = prepareExecutor();
        final ProcessResult result = executor.executeNoTimeout();
        iterations++;

        final String outLog = extractOutAsString();
        final String errLog = extractErrorOutAsString();

        printLogs(outLog, errLog);

        if (doesNeedOneMoreAttempt(result, outLog, errLog)) {
          if (iterations > 10) {
            throw new MojoExecutionException("Too many iterations detected, may be some loop and bug at mojo " + this.getClass().getName());
          }
          getLog().warn("Make one more attempt...");
        } else {
          assertProcessResult(result);
          break;
        }
      }
    } catch (IOException ex) {
      error = true;
      throw new MojoExecutionException(ex.getMessage(), ex);
    } catch (InterruptedException ex) {
      error = true;
    } finally {
      afterExecution(error);
    }
  }

  public void beforeExecution() {

  }

  public void afterExecution(final boolean error) throws MojoFailureException {

  }

  public boolean enforcePrintOutput() {
    return false;
  }

  @Nonnull
  private String extractOutAsString() {
    return new String(this.consoleOutBuffer.toByteArray(), Charset.defaultCharset());
  }

  @Nonnull
  private String extractErrorOutAsString() {
    return new String(this.consoleErrBuffer.toByteArray(), Charset.defaultCharset());
  }

  protected void printLogs(@Nonnull final String outLog, @Nonnull final String errLog) {
    if ((enforcePrintOutput() || getLog().isDebugEnabled()) && !outLog.isEmpty()) {
      getLog().info("");
      getLog().info("---------Exec.Out---------");
      getLog().info(outLog);
      getLog().info("");
    }

    if (!errLog.isEmpty()) {
      getLog().error("");
      getLog().error("---------Exec.Err---------");
      getLog().error(errLog);
      getLog().error("");
    }

  }

  private void assertProcessResult(@Nonnull final ProcessResult result) throws MojoFailureException {
    final int code = result.getExitValue();
    if (code != 0) {
      throw new MojoFailureException("Process exit code : " + code);
    }
  }

  @Nonnull
  @MustNotContainNull
  public abstract String[] getTailArguments();

  @Nonnull
  @MustNotContainNull
  public String[] getOptionalExtraTailArguments() {
    return ArrayUtils.EMPTY_STRING_ARRAY;
  }

  @Nonnull
  public String getMainGoExecName() {
    return "bin" + File.separatorChar + "go";
  }

  @Nonnull
  public abstract String getGoCommand();

  @Nonnull
  @MustNotContainNull
  public abstract String[] getCommandFlags();

  private void addEnvVar(@Nonnull final ProcessExecutor executor, @Nonnull final String name, @Nonnull final String value) {
    getLog().info(" $" + name + " = " + value);
    executor.environment(name, value);
  }

  @Nonnull
  protected static String adaptExecNameForOS(@Nonnull final String execName) {
    return execName + (SystemUtils.IS_OS_WINDOWS ? ".exe" : "");
  }

  @Nonnull
  private static String getPathToFolder(@Nonnull final File path) {
    String text = path.getAbsolutePath();
    if (!text.endsWith("/") && !text.endsWith("\\")) {
      text = text + File.separatorChar;
    }
    return text;
  }

  @Nonnull
  private ProcessExecutor prepareExecutor() throws IOException, MojoFailureException {
    initConsoleBuffers();
    final String execNameAdaptedForOs = adaptExecNameForOS(getMainGoExecName());
    final File detectedRoot = findGoRoot();
    final File executableFile = new File(getPathToFolder(detectedRoot) + FilenameUtils.normalize(GetUtils.ensureNonNull(getUseGoTool(), execNameAdaptedForOs)));

    if (!executableFile.isFile()) {
      throw new IOException("Can't find executable file : " + executableFile);
    } else {
      logOptionally("Executable go tool file detected : " + executableFile);
    }

    final List<String> commandLine = new ArrayList<String>();
    commandLine.add(executableFile.getAbsolutePath());
    commandLine.add(getGoCommand());

    for (final String s : getCommandFlags()) {
      commandLine.add(s);
    }

    for (final String s : getBuildFlags()) {
      commandLine.add(s);
    }

    for (final String s : getTailArguments()) {
      commandLine.add(s);
    }

    for (final String s : getOptionalExtraTailArguments()) {
      commandLine.add(s);
    }

    final StringBuilder cli = new StringBuilder();
    int index = 0;
    for (final String s : commandLine) {
      if (cli.length() > 0) {
        cli.append(' ');
      }
      if (index == 0) {
        cli.append(execNameAdaptedForOs);
      } else {
        cli.append(s);
      }
      index++;
    }

    logOptionally("Command line : " + cli.toString());

    final ProcessExecutor result = new ProcessExecutor(commandLine);

    final File sourcesFile = getSources(true);
    logOptionally("GoLang project folder : " + sourcesFile);

    result.directory(sourcesFile);

    getLog().info("");
    getLog().info("....Environment vars....");

    addEnvVar(result, "GOROOT", detectedRoot.getAbsolutePath());

    final File gopath = findGoPath(true);
    addEnvVar(result, "GOPATH", gopath.getAbsolutePath());

    final String trgtOs = this.getTargetOS();
    final String trgtArch = this.getTargetArch();

    if (trgtOs != null) {
      addEnvVar(result, "GOOS", trgtOs);
    }

    if (trgtArch != null) {
      addEnvVar(result, "GOARCH", trgtArch);
    }

    final File gorootbootstrap = findGoRootBootstrap(true);
    if (gorootbootstrap != null) {
      addEnvVar(result, "GOROOT_BOOTSTRAP", gorootbootstrap.getAbsolutePath());
    }

    final Properties envProperties = getEnv();

    for (final String key : envProperties.stringPropertyNames()) {
      addEnvVar(result, key, envProperties.getProperty(key));
    }

    getLog().info("........................");

    result.redirectOutput(this.consoleOutBuffer);
    result.redirectError(this.consoleErrBuffer);

    return result;
  }
}

# 2.1.7 (18-feb-2018)
- fixed target file extension in maven archetypes [#44](https://github.com/raydac/mvn-golang/issues/44)
- added `target386` to provide value for `$GO386` environment variable
- improved GOPATH value processing, multi-folder value allowed
- added flag to disable SSL certificate check for HTTPS connections, `disableSSLcheck`, by default it is `false`
- improved Golang SDK list load [#24](https://github.com/raydac/mvn-golang/issues/24)
- added `args` attribute to the `run` mojo to provide tail command line arguments.
- added processing of maven session offline mode 
- improved proxy server settings processing to process NTLM authorisation
- removed maven-enforcer-plugin because it throws NPE for old maven versions

# 2.1.6 (27-aug-2017)
- implemented file locker to synchronize SDK loading between JVM processes, if cache folder is shared
- improved `get` mojo behavior during branch, tag and revision processing
- improved `get` mojo, added `deleteCommonPkg` flag to delete whole common `pkg` folder, by default false
- improved logging
- added property `supposeSdkArchiveFileName` to suppose SDK archive file name if it is not presented in common SDK list, active by default
- minimal version of Java increased to 1.7

# 2.1.5 (03-jul-2017)
- added archetype for multimodule project `mvn-golang-hello-multi` 
- added `customCvsOptions` into `get` mojo to provide custom options for CVS operation.
- improved `get` mojo, added auto-search of CVS folder in package folder hierarchy, it can be disabled with `<disableCvsAutosearch>true</disableCvsAutosearch>`, #23
- improved `get` mojo, added way to define relative path to CVS folder in `src` folder through `<relativePathToCvsFolder>`, by default the path extracted from package name

# 2.1.4 (24-jun-2017)
- added support of BAZAAR CVS (experimental)
- fixed order of processing of CVS branch, tag and revision in `get` mojo
- added `enforceDeletePackageFiles` flag into `get` mojo to enforce deletion of package sources and compiled version in local repository
- fixed processing of `revision` for CVS

# 2.1.3 (14-apr-2017)
- Improved `go-hello-test` archetype to generate [Intellij Idea Go plugin project structure](https://plugins.jetbrains.com/plugin/5047-go-language-golang-org-support-plugin)
- Added flag `enforceGoPathToEnd` to enforce changing of folder list order in new generated GOPATH
- Added list parameter `ldFlags` for `buildMojo` to define linker flags.
- Added boolean flag `strip` for `buildMojo` to remove symbol table and DWARF from the result file.
- Added parameter `buildMode` for `buildMojo` to define [Go build mode](https://golang.org/cmd/go/#hdr-Description_of_build_modes)

# 2.1.2 (07-nov-2016)
- Added `skip` attribute to skip execution of mojo
- [#10](https://github.com/raydac/mvn-golang/issues/10), Added way to disable providing of $GOBIN through pseudo-path __NONE__
- Changed maven phase for build from `compile` to `package` (to prevent build start before tests)
- Enforced console output for `test` even in non-verbose mode
- Added default packages `./...` for `fmt`,`vet`,`fix` and `test` tasks
- Added `maven.test.failure.ignore` and `test` properties processing into `test` goal, also allowed method regex template after `#` like in surefire

# 2.1.1 (21-aug-2016)
- [#9](https://github.com/raydac/mvn-golang/issues/9), Added attribute `targetArm` to provide $GOARM value
- Added support of proxy server [#8](https://github.com/raydac/mvn-golang/issues/8), added flag `useMavenProxy` to use proxy server defined either through maven settings.xml file or the `proxy` configuration section of the plugin.
- Improved `clean` mojo, added flags to clean Go path folder defined through `goPath` parameter, and added flag to delete whole `storeFolder`
- Added flag `ignoreErrorExitCode` to prevent failure for error exit code, it is useful in some test cases.
- Added parameter `reportsFolder` to define folder where some reports will be placed.
- Added parameters `outLogFile` and `errLogFile` to save execution console log as files in the report folder. By default such reports are not saved.
- Console log for `test` will be shown in maven log only in verbose mode 

# 2.1.0 (28-may-2016)
- Output of environment variables has been moved under the `verbose` flag
- __Added `mvninstall` goal which saves artifact into local maven repository during `install` phase,[#2 request](https://github.com/raydac/mvn-golang/issues/2)__
- __Added support of branches and tags into `get`, it works for Git, Hg and SVN repositories__
- Improved archetype template, added example of test
- __Fixed issue [#3 "cannot run tests"](https://github.com/raydac/mvn-golang/issues/3)__

# 2.0.0 (17-apr-2016)
- __Added maven archetype `mvn-golang-hello` to generate minimal GoLang "Hello World!" project__
- Added mojo for `run` command.
- __Removed `<findExecInGoPath>` property because the logic of search executable file has been reworked__
- Added `goBin` parameter to provide $GOBIN value
- Improved CLEAN to delete also the project target folder
- The Banner is hidden by default
- __Changed project folder structure to be closer to GoLang projects__
- __Added life-cycle for packaging `mvn-golang` with support of the standard GoLang project hierarchy, as example see adapted [the Hello world example for the case](https://github.com/raydac/mvn-golang/blob/master/mvn-golang-examples/mvn-golang-example-helloworld/pom.xml)__
- Improved logging of command console output, now it is split to lines and every line logged separately
- Added support for loading of archives with Content-type `application/x-gzip`
- Increased number of test examples
- Build of example applications moved to the special profile `examples`

# 1.1.0 (05-apr-2016)
- Added [test example for `gomobile` for Android ARM 7](https://github.com/raydac/mvn-golang/tree/master/mvn-golang-examples/mvn-golang-example-gomobile) 
- Added `<findExecInGoPath>`, it allows to find golang tool in $GOPATH instead of $GOROOT (by default `false`)
- Added `<echo>` and `<echoWarn>` to print echo messages into maven log
- Added `<exec>` parameter to define gotool name (by default `go`)
- Added `<execSubpath>` parameter to provide sub-path in SDK root to find golang tool (by default `bin`)
- Renamed parameter `<name>` to `<resultName>` and `<target>` to `<resultFolder>` for `build`
- Fixed racing issue for the maven `-T4` flag
- Fixed "Truncated TAR archive exception" for Mac OS tar.gz archive
- Removed predefined values for `<goVersion>` and `<osx>`
- Minor refactoring

# 1.0.0 (26-mar-2016)
- initial version

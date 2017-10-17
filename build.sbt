import Util._
import Dependencies._
import Scripted._

def baseVersion = "1.1.0-SNAPSHOT"
def internalPath = file("internal")

lazy val compilerBridgeScalaVersions = List(scala212, scala213, scala211, scala210)
lazy val compilerBridgeTestScalaVersions = List(scala212, scala211, scala210)

def mimaSettings: Seq[Setting[_]] = Seq(
  mimaPreviousArtifacts := Set(
    "1.0.0", "1.0.1", "1.0.2", "1.0.3", "1.0.4", "1.0.5",
  ) map (version =>
    organization.value %% moduleName.value % version
      cross (if (crossPaths.value) CrossVersion.binary else CrossVersion.disabled)
  ),
)

def commonSettings: Seq[Setting[_]] = Seq(
  scalaVersion := scala212,
  // publishArtifact in packageDoc := false,
  resolvers += Resolver.typesafeIvyRepo("releases"),
  resolvers += Resolver.sonatypeRepo("snapshots"),
  resolvers += "bintray-sbt-maven-releases" at "https://dl.bintray.com/sbt/maven-releases/",
  resolvers += Resolver.url(
    "bintray-sbt-ivy-snapshots",
    new URL("https://dl.bintray.com/sbt/ivy-snapshots/"))(Resolver.ivyStylePatterns),
  resolvers += ScriptedResolver,
  // concurrentRestrictions in Global += Util.testExclusiveRestriction,
  testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-w", "1"),
  javacOptions in compile ++= Seq("-Xlint", "-Xlint:-serial"),
  crossScalaVersions := Seq(scala211, scala212),
  publishArtifact in Test := false,
  commands ++= Seq(publishBridgesAndTest, publishBridgesAndSet, crossTestBridges),
  scalacOptions ++= Seq(
    "-YdisableFlatCpCaching",
    "-target:jvm-1.8",
  )
)

def relaxNon212: Seq[Setting[_]] = Seq(
  scalacOptions := {
    val old = scalacOptions.value
    scalaBinaryVersion.value match {
      case "2.12" => old
      case _ =>
        old filterNot Set(
          "-Xfatal-warnings",
          "-deprecation",
          "-Ywarn-unused",
          "-Ywarn-unused-import",
          "-YdisableFlatCpCaching",
        )
    }
  }
)

def minimalSettings: Seq[Setting[_]] = commonSettings

// def minimalSettings: Seq[Setting[_]] =
//   commonSettings ++ customCommands ++
//   publishPomSettings ++ Release.javaVersionCheckSettings

def baseSettings: Seq[Setting[_]] =
  minimalSettings
//   minimalSettings ++ baseScalacOptions ++ Licensed.settings ++ Formatting.settings

def addBaseSettingsAndTestDeps(p: Project): Project =
  p.settings(baseSettings).configure(addTestDependencies)

val altLocalRepoName = "alternative-local"
val altLocalRepoPath = sys.props("user.home") + "/.ivy2/sbt-alternative"
lazy val altLocalResolver = Resolver.file(
  altLocalRepoName,
  file(sys.props("user.home") + "/.ivy2/sbt-alternative"))(Resolver.ivyStylePatterns)
lazy val altLocalPublish =
  TaskKey[Unit]("alt-local-publish", "Publishes an artifact locally to an alternative location.")
def altPublishSettings: Seq[Setting[_]] =
  Seq(
    resolvers += altLocalResolver,
    altLocalPublish := {
      import sbt.librarymanagement._
      import sbt.internal.librarymanagement._
      val config = (Keys.publishLocalConfiguration).value
      val moduleSettings = (Keys.moduleSettings).value
      val ivy = new IvySbt((ivyConfiguration.value))

      val module = new ivy.Module(moduleSettings)
      val newConfig = config.withResolverName(altLocalRepoName).withOverwrite(false)
      streams.value.log.info(s"Publishing $module to local repo: $altLocalRepoName")
      IvyActions.publish(module, newConfig, streams.value.log)
    }
  )

val noPublish: Seq[Setting[_]] = List(
  publish := {},
  publishLocal := {},
  publishArtifact in Compile := false,
  publishArtifact in Test := false,
  publishArtifact := false,
  skip in publish := true,
)

lazy val zincRoot: Project = (project in file("."))
// configs(Sxr.sxrConf).
  .aggregate(
    zinc,
    zincTesting,
    zincPersist,
    zincCore,
    zincIvyIntegration,
    zincCompile,
    zincCompileCore,
    compilerInterface,
    compilerBridge,
    zincBenchmarks,
    zincApiInfo,
    zincClasspath,
    zincClassfile,
    zincScripted
  )
  .settings(
    inThisBuild(
      Seq(
        git.baseVersion := baseVersion,
        // https://github.com/sbt/sbt-git/issues/109
        // Workaround from https://github.com/sbt/sbt-git/issues/92#issuecomment-161853239
        git.gitUncommittedChanges := {
          val statusCommands = Seq(
            Seq("diff-index", "--cached", "HEAD"),
            Seq("diff-index", "HEAD"),
            Seq("diff-files"),
            Seq("ls-files", "--exclude-standard", "--others")
          )
          // can't use git.runner.value because it's a task
          val runner = com.typesafe.sbt.git.ConsoleGitRunner
          val dir = baseDirectory.value
          // sbt/zinc#334 Seemingly "git status" resets some stale metadata.
          runner("status")(dir, com.typesafe.sbt.git.NullLogger)
          val uncommittedChanges = statusCommands flatMap { c =>
            val res = runner(c: _*)(dir, com.typesafe.sbt.git.NullLogger)
            if (res.isEmpty) Nil else Seq(c -> res)
          }

          val un = uncommittedChanges.nonEmpty
          if (un) {
            uncommittedChanges foreach {
              case (cmd, res) =>
                sLog.value debug s"""Uncommitted changes found via "${cmd mkString " "}":\n${res}"""
            }
          }
          un
        },
        version := {
          val v = version.value
          if (v contains "SNAPSHOT") git.baseVersion.value
          else v
        },
        bintrayPackage := "zinc",
        scmInfo := Some(ScmInfo(url("https://github.com/sbt/zinc"), "git@github.com:sbt/zinc.git")),
        description := "Incremental compiler of Scala",
        homepage := Some(url("https://github.com/sbt/zinc")),
        developers +=
          Developer("jvican", "Jorge Vicente Cantero", "@jvican", url("https://github.com/jvican")),
        scalafmtOnCompile in Sbt := false,
        releaseEarlyWith := BintrayPublisher,
        publishArtifact in (Compile, Keys.packageDoc) :=
         publishDocAndSourceArtifact.value,
        publishArtifact in (Compile, Keys.packageSrc) :=
         publishDocAndSourceArtifact.value,
        cachedPublishLocal := cachedPublishLocalImpl.value
      )),
    minimalSettings,
    otherRootSettings,
    noPublish,
    scriptedPublish := cachedPublishLocalImpl.all(ScopeFilter(inAnyProject)).value,
    name := "zinc Root",
  )

lazy val zinc = (project in file("zinc"))
  .dependsOn(
    zincCore,
    zincPersist,
    zincCompileCore,
    zincClassfile,
    zincIvyIntegration % "compile->compile;test->test",
    zincTesting % Test
  )
  .configure(addBaseSettingsAndTestDeps)
  .settings(
    name := "zinc",
    mimaSettings,
  )

lazy val zincTesting = (project in internalPath / "zinc-testing")
  .settings(
    minimalSettings,
    noPublish,
    name := "zinc Testing",
    libraryDependencies ++= Seq(scalaCheck, scalatest, junit, sjsonnewScalaJson.value)
  )
  .configure(addSbtLmCore, addSbtLmIvy)

lazy val zincCompile = (project in file("zinc-compile"))
  .dependsOn(zincCompileCore, zincCompileCore % "test->test")
  .configure(addBaseSettingsAndTestDeps)
  .settings(
    name := "zinc Compile",
    mimaSettings,
  )
  .configure(addSbtUtilTracking)

// Persists the incremental data structures using Protobuf
lazy val zincPersist = (project in internalPath / "zinc-persist")
  .dependsOn(zincCore, zincCore % "test->test")
  .configure(addBaseSettingsAndTestDeps)
  .settings(
    name := "zinc Persist",
    libraryDependencies += sbinary,
    compileOrder := sbt.CompileOrder.Mixed,
    PB.targets in Compile := List(scalapb.gen() -> (sourceManaged in Compile).value),
    mimaSettings,
  )

// Implements the core functionality of detecting and propagating changes incrementally.
//   Defines the data structures for representing file fingerprints and relationships and the overall source analysis
lazy val zincCore = (project in internalPath / "zinc-core")
  .dependsOn(
    zincApiInfo,
    zincClasspath,
    compilerInterface,
    compilerBridge % Test,
    zincTesting % Test
  )
  .configure(addBaseSettingsAndTestDeps)
  .settings(
    // we need to fork because in unit tests we set usejavacp = true which means
    // we are expecting all of our dependencies to be on classpath so Scala compiler
    // can use them while constructing its own classpath for compilation
    fork in Test := true,
    // needed because we fork tests and tests are ran in parallel so we have multiple Scala
    // compiler instances that are memory hungry
    javaOptions in Test += "-Xmx1G",
    name := "zinc Core",
    compileOrder := sbt.CompileOrder.Mixed,
    mimaSettings,
  )
  .configure(addSbtIO, addSbtUtilLogging, addSbtUtilRelation)

lazy val zincBenchmarks = (project in internalPath / "zinc-benchmarks")
  .dependsOn(compilerInterface % "compile->compile;compile->test")
  .dependsOn(compilerBridge, zincCore, zincTesting % Test)
  .enablePlugins(JmhPlugin)
  .settings(
    noPublish,
    name := "Benchmarks of Zinc and the compiler bridge",
    libraryDependencies ++= Seq(
      "org.eclipse.jgit" % "org.eclipse.jgit" % "4.6.0.201612231935-r",
      "net.openhft" % "affinity" % "3.0.6"
    ),
    scalaVersion := scala212,
    crossScalaVersions := Seq(scala211, scala212),
    javaOptions in Test += "-Xmx600M -Xms600M",
  )

lazy val zincIvyIntegration = (project in internalPath / "zinc-ivy-integration")
  .dependsOn(zincCompileCore, zincTesting % Test)
  .settings(
    baseSettings,
    name := "zinc Ivy Integration",
    compileOrder := sbt.CompileOrder.ScalaThenJava,
    mimaSettings,
  )
  .configure(addSbtLmCore, addSbtLmIvyTest)

// sbt-side interface to compiler.  Calls compiler-side interface reflectively
lazy val zincCompileCore = (project in internalPath / "zinc-compile-core")
  .enablePlugins(ContrabandPlugin)
  .dependsOn(
    compilerInterface % "compile;test->test",
    zincClasspath,
    zincApiInfo,
    zincClassfile,
    zincTesting % Test
  )
  .configure(addBaseSettingsAndTestDeps)
  .settings(
    name := "zinc Compile Core",
    libraryDependencies ++= Seq(scalaCompiler.value % Test, launcherInterface, parserCombinator),
    unmanagedJars in Test := Seq(packageSrc in compilerBridge in Compile value).classpath,
    managedSourceDirectories in Compile +=
      baseDirectory.value / "src" / "main" / "contraband-java",
    sourceManaged in (Compile, generateContrabands) := baseDirectory.value / "src" / "main" / "contraband-java",
    mimaSettings,
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._
      import com.typesafe.tools.mima.core.ProblemFilters._
      Seq(
        // PositionImpl is a private class only invoked in the same source.
        exclude[FinalClassProblem]("sbt.internal.inc.javac.DiagnosticsReporter$PositionImpl"),
        exclude[DirectMissingMethodProblem]("sbt.internal.inc.javac.DiagnosticsReporter#PositionImpl.this"),
      )
    },
  )
  .configure(addSbtUtilLogging, addSbtIO, addSbtUtilControl)

// defines Java structures used across Scala versions, such as the API structures and relationships extracted by
//   the analysis compiler phases and passed back to sbt.  The API structures are defined in a simple
//   format from which Java sources are generated by the sbt-contraband plugin.
lazy val compilerInterface = (project in internalPath / "compiler-interface")
  .enablePlugins(ContrabandPlugin)
  .settings(
    minimalSettings,
    // javaOnlySettings,
    name := "Compiler Interface",
    // Use the smallest Scala version in the compilerBridgeScalaVersions
    // Technically the scalaVersion shouldn't have any effect since scala library is not included,
    // but given that Scala 2.10 compiler cannot parse Java 8 source, it's probably good to keep this.
    crossScalaVersions := Seq(scala210),
    scalaVersion := scala210,
    relaxNon212,
    libraryDependencies ++= Seq(scalaLibrary.value % Test),
    exportJars := true,
    resourceGenerators in Compile += Def
      .task(
        generateVersionFile(
          version.value,
          resourceManaged.value,
          streams.value,
          compile in Compile value
        )
      )
      .taskValue,
    managedSourceDirectories in Compile +=
      baseDirectory.value / "src" / "main" / "contraband-java",
    sourceManaged in (Compile, generateContrabands) := baseDirectory.value / "src" / "main" / "contraband-java",
    crossPaths := false,
    autoScalaLibrary := false,
    altPublishSettings,
    mimaSettings,
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._
      import com.typesafe.tools.mima.core.ProblemFilters._
      Seq(
        exclude[ReversedMissingMethodProblem]("xsbti.compile.ExternalHooks#Lookup.hashClasspath"),
      )
    },
  )
  .configure(addSbtUtilInterface)

val scriptedPublish = taskKey[Unit]("Publishes all the Zinc artifacts for scripted")
val cachedPublishLocal = taskKey[Unit]("Publishes a project if it hasn't been published before.")

def wrapIn(color: String, content: String): String = {
  import sbt.internal.util.ConsoleAppender
  if (!ConsoleAppender.formatEnabledInEnv) content
  else color + content + scala.Console.RESET
}

/**
 * Compiler-side interface to compiler that is compiled against the compiler being used either in advance or on the fly.
 * Includes API and Analyzer phases that extract source API and relationships.
 * As this is essentially implementations of the compiler-interface (per Scala compiler),
 * the code here should not be consumed without going through the classloader trick and the interface.
 * Due to the hermetic nature of the bridge, there's no necessity to keep binary compatibility across Zinc versions,
 * and therefore there's no `mimaSettings` added.
 * For the case of Scala 2.13 bridge, we didn't even have the bridge to compare against when Zinc 1.0.0 came out.
 */
lazy val compilerBridge: Project = (project in internalPath / "compiler-bridge")
  .dependsOn(compilerInterface)
  .settings(
    baseSettings,
    crossScalaVersions := compilerBridgeScalaVersions,
    relaxNon212,
    libraryDependencies += scalaCompiler.value % "provided",
    autoScalaLibrary := false,
    // precompiledSettings,
    name := "Compiler Bridge",
    exportJars := true,
    inBoth(unmanagedSourceDirectories ++= scalaPartialVersion.value.collect {
      case (2, y) if y == 10            => new File(scalaSource.value.getPath + "_2.10")
      case (2, y) if y == 11 || y == 12 => new File(scalaSource.value.getPath + "_2.11-12")
      case (2, y) if y >= 13            => new File(scalaSource.value.getPath + "_2.13")
    }.toList),
    // Use a bootstrap compiler bridge to compile the compiler bridge.
    scalaCompilerBridgeSource := {
      val old = scalaCompilerBridgeSource.value
      scalaVersion.value match {
        case x if x startsWith "2.13." => ("org.scala-sbt" % "compiler-bridge_2.13.0-M2" % "1.1.0-M1-bootstrap2" % Compile).sources()
        case _ => old
      }
    },
    altPublishSettings,
    cachedPublishLocal := cachedPublishLocal.dependsOn(cachedPublishLocal.in(zincApiInfo)).value,
    // Make sure that the sources are published for the bridge because we need them to compile it
    publishArtifact in (Compile, packageSrc) := true,
  )

/**
 * Tests for the compiler bridge.
 * This is split into a separate subproject because testing introduces more dependencies
 * (Zinc API Info, which transitively depends on IO).
 */
lazy val compilerBridgeTest = (project in internalPath / "compiler-bridge-test")
  .dependsOn(compilerBridge, compilerInterface % "test->test", zincApiInfo % "test->test")
  .settings(
    name := "Compiler Bridge Test",
    baseSettings,
    relaxNon212,
    // we need to fork because in unit tests we set usejavacp = true which means
    // we are expecting all of our dependencies to be on classpath so Scala compiler
    // can use them while constructing its own classpath for compilation
    fork in Test := true,
    // needed because we fork tests and tests are ran in parallel so we have multiple Scala
    // compiler instances that are memory hungry
    javaOptions in Test += "-Xmx1G",
    crossScalaVersions := compilerBridgeTestScalaVersions,
    libraryDependencies += scalaCompiler.value,
    altPublishSettings,
    skip in publish := true,
  )

val scalaPartialVersion = Def setting (CrossVersion partialVersion scalaVersion.value)

def inBoth(ss: Setting[_]*): Seq[Setting[_]] = Seq(Compile, Test) flatMap (inConfig(_)(ss))

// defines operations on the API of a source, including determining whether it has changed and converting it to a string
//   and discovery of Projclasses and annotations
lazy val zincApiInfo = (project in internalPath / "zinc-apiinfo")
  .dependsOn(compilerInterface, zincClassfile % "compile;test->test")
  .configure(addBaseSettingsAndTestDeps)
  .settings(
    name := "zinc ApiInfo",
    crossScalaVersions := compilerBridgeTestScalaVersions,
    relaxNon212,
    mimaSettings,
  )

// Utilities related to reflection, managing Scala versions, and custom class loaders
lazy val zincClasspath = (project in internalPath / "zinc-classpath")
  .dependsOn(compilerInterface)
  .configure(addBaseSettingsAndTestDeps)
  .settings(
    name := "zinc Classpath",
    crossScalaVersions := compilerBridgeTestScalaVersions,
    relaxNon212,
    libraryDependencies ++= Seq(scalaCompiler.value, launcherInterface),
    mimaSettings,
  )
  .configure(addSbtIO)

// class file reader and analyzer
lazy val zincClassfile = (project in internalPath / "zinc-classfile")
  .dependsOn(compilerInterface % "compile;test->test")
  .configure(addBaseSettingsAndTestDeps)
  .settings(
    name := "zinc Classfile",
    crossScalaVersions := compilerBridgeTestScalaVersions,
    relaxNon212,
    mimaSettings,
  )
  .configure(addSbtIO, addSbtUtilLogging)

// re-implementation of scripted engine
lazy val zincScripted = (project in internalPath / "zinc-scripted")
  .dependsOn(zinc, zincIvyIntegration % "test->test")
  .settings(
    minimalSettings,
    noPublish,
    name := "zinc Scripted",
  )
  .configure(addSbtUtilScripted)

lazy val crossTestBridges = {
  Command.command("crossTestBridges") { state =>
    (compilerBridgeTestScalaVersions.flatMap { (bridgeVersion: String) =>
      // Note the ! here. You need this so compilerInterface gets forced to the scalaVersion
      s"++ $bridgeVersion!" ::
        s"${compilerBridgeTest.id}/test" ::
        Nil
    }) :::
      (s"++ $scala212!" ::
      state)
  }
}

lazy val publishBridgesAndSet = {
  Command.args("publishBridgesAndSet", "<version>") { (state, args) =>
    require(args.nonEmpty, "Missing Scala version argument.")
    val userScalaVersion = args.mkString("")
    s"${compilerInterface.id}/publishLocal" ::
      compilerBridgeScalaVersions.flatMap { (bridgeVersion: String) =>
      s"++ $bridgeVersion!" ::
        s"${compilerBridge.id}/publishLocal" :: Nil
    } :::
      s"++ $userScalaVersion!" ::
      state
  }
}

lazy val publishBridgesAndTest = Command.args("publishBridgesAndTest", "<version>") {
  (state, args) =>
    require(args.nonEmpty,
            "Missing arguments to publishBridgesAndTest. Maybe quotes are missing around command?")
    val version = args mkString ""
    s"${compilerInterface.id}/publishLocal" ::
      (compilerBridgeScalaVersions.flatMap { (bridgeVersion: String) =>
      s"++ $bridgeVersion" ::
        s"${compilerBridge.id}/publishLocal" :: Nil
    }) :::
      s"++ $version" ::
      s"zincRoot/scalaVersion" ::
      s"zincRoot/test" ::
      s"zincRoot/scripted" ::
      state
}

val dir = IO.createTemporaryDirectory
val dirPath = dir.getAbsolutePath
lazy val tearDownBenchmarkResources = taskKey[Unit]("Remove benchmark resources.")
tearDownBenchmarkResources in ThisBuild := { IO.delete(dir) }

addCommandAlias(
  "runBenchmarks",
  s""";zincBenchmarks/run $dirPath
     |;zincBenchmarks/jmh:run -p _tempDir=$dirPath -prof gc
     |;tearDownBenchmarkResources
   """.stripMargin
)

lazy val otherRootSettings = Seq(
  Scripted.scriptedBufferLog := true,
  Scripted.scriptedPrescripted := { addSbtAlternateResolver _ },
  Scripted.scripted := scriptedTask.evaluated,
  Scripted.scriptedUnpublished := scriptedUnpublishedTask.evaluated,
  Scripted.scriptedSource := (sourceDirectory in zinc).value / "sbt-test",
  publishAll := {
    val _ = cachedPublishLocal.all(ScopeFilter(inAnyProject)).value
  }
)

def scriptedTask: Def.Initialize[InputTask[Unit]] = Def.inputTask {
  val result = scriptedSource(dir => (s: State) => scriptedParser(dir)).parsed
  publishAll.value
  // These two projects need to be visible in a repo even if the default
  // local repository is hidden, so we publish them to an alternate location and add
  // that alternate repo to the running scripted test (in Scripted.scriptedpreScripted).
  (altLocalPublish in compilerInterface).value
  (altLocalPublish in compilerBridge).value
  doScripted(
    (fullClasspath in zincScripted in Test).value,
    (scalaInstance in zincScripted).value,
    scriptedSource.value,
    result,
    scriptedBufferLog.value,
    scriptedPrescripted.value
  )
}

def addSbtAlternateResolver(scriptedRoot: File) = {
  val resolver = scriptedRoot / "project" / "AddResolverPlugin.scala"
  if (!resolver.exists) {
    IO.write(
      resolver,
      s"""import sbt._
         |import Keys._
         |
         |object AddResolverPlugin extends AutoPlugin {
         |  override def requires = sbt.plugins.JvmPlugin
         |  override def trigger = allRequirements
         |
         |  override lazy val projectSettings = Seq(resolvers += alternativeLocalResolver)
         |  lazy val alternativeLocalResolver = Resolver.file("$altLocalRepoName", file("$altLocalRepoPath"))(Resolver.ivyStylePatterns)
         |}
         |""".stripMargin
    )
  }
}

def scriptedUnpublishedTask: Def.Initialize[InputTask[Unit]] = Def.inputTask {
  val result = scriptedSource(dir => (s: State) => scriptedParser(dir)).parsed
  doScripted(
    (fullClasspath in zincScripted in Test).value,
    (scalaInstance in zincScripted).value,
    scriptedSource.value,
    result,
    scriptedBufferLog.value,
    scriptedPrescripted.value
  )
}

lazy val publishAll = TaskKey[Unit]("publish-all")
lazy val publishLauncher = TaskKey[Unit]("publish-launcher")

inThisBuild(Seq(
  whitesourceProduct                   := "Lightbend Reactive Platform",
  whitesourceAggregateProjectName      := "sbt-zinc-master",
  whitesourceAggregateProjectToken     := "4b57f35176864c6397b872277d51bc27b89503de0f1742b8bc4dfa2e33b95c5c",
  whitesourceIgnoredScopes             += "scalafmt",
  whitesourceFailOnError               := sys.env.contains("WHITESOURCE_PASSWORD"), // fail if pwd is present
  whitesourceForceCheckAllDependencies := true,
))

// Defines a resolver that is used to publish only for local testing via scripted
val ScriptedResolverId = "zinc-scripted-local"
val ScriptedResolveCacheDir: File = file(sys.props("user.dir") + s"/.ivy2/$ScriptedResolverId")
val ScriptedResolver: Resolver =
  Resolver.file(ScriptedResolverId, ScriptedResolveCacheDir)(Resolver.ivyStylePatterns)

/**
 * This setting figures out whether the version is a snapshot or not and configures
 * the source and doc artifacts that are published by the build.
 *
 * Snapshot is a term with no clear definition. In this code, a snapshot is a revision
 * that has either build or time metadata in its representation. In those cases, the
 * build will not publish doc and source artifacts by any of the publishing actions.
 */
val publishDocAndSourceArtifact: Def.Initialize[Boolean] = Def.setting {
  import sbtdynver.{ GitDescribeOutput, DynVerPlugin }
  import DynVerPlugin.{ autoImport => DynVerKeys }
  def isDynVerSnapshot(gitInfo: Option[GitDescribeOutput], defaultValue: Boolean): Boolean = {
    val isStable = gitInfo.map { info =>
      info.ref.value.startsWith("v") &&
      (info.commitSuffix.distance <= 0 || info.commitSuffix.sha.isEmpty)
    }
    val isNewSnapshot =
      isStable.map(stable => !stable || defaultValue)
    // Return previous snapshot definition in case users has overridden version
    isNewSnapshot.getOrElse(defaultValue)
  }

  // We publish doc and source artifacts if the version is not a snapshot
  !isDynVerSnapshot(DynVerKeys.dynverGitDescribeOutput.value, Keys.isSnapshot.value)
}

import scala.Console
val P = s"[${Console.BOLD}${Console.CYAN}scripted${Console.RESET}]"
val cachedPublishLocalImpl: Def.Initialize[Task[Unit]] = Def.taskDyn {
  if ((Keys.skip in Keys.publish).value) Def.task(())
  else
    Def.taskDyn {
      import sbt.util.Logger.{ Null => NoLogger }
      val logger = Keys.streams.value.log

      // Find out the configuration of this task to invoke source dirs in the right place
      val taskConfig = Keys.resolvedScoped.value.scope.config
      val currentConfig: sbt.ConfigKey = taskConfig.fold(identity, Compile, Compile)

      // Important to make it transitive, we just want to check if a jar exists
      val moduleID = Keys.projectID.value.intransitive()
      val scalaModule = Keys.scalaModuleInfo.value
      val ivyConfig = Keys.ivyConfiguration.value
      val options = ivyConfig.updateOptions

      // If it's another thing, just fail! We must have an inline ivy config here.
      val inlineConfig = ivyConfig.asInstanceOf[InlineIvyConfiguration]
      val fasterIvyConfig: InlineIvyConfiguration = inlineConfig
        .withResolvers(Vector(ScriptedResolver))
        .withChecksums(Vector())
        // We can do this because we resolve intransitively and nobody but this task publishes
        .withLock(None)

      val resolution = sbt.librarymanagement.ivy.IvyDependencyResolution(fasterIvyConfig)
      val result = resolution.retrieve(moduleID, scalaModule, ScriptedResolveCacheDir, NoLogger)
      result match {
        case l: Left[_, _] => publishLocalWrapper(moduleID, fasterIvyConfig, false)
        case Right(resolved) =>
          Def.taskDyn {
            val projectName = Keys.name.value
            val baseDirectory = Keys.baseDirectory.value.toPath()
            val sourceDirs = Keys.sourceDirectories.in(currentConfig).value
            val resourceDirs = Keys.resourceDirectories.in(currentConfig).value
            val allDirs = sourceDirs ++ resourceDirs
            val files = allDirs.flatMap(sbt.Path.allSubpaths(_)).toIterator.map(_._1)

            val allJars = resolved.filter(_.getPath().endsWith(".jar"))
            val lastPublicationTime = allJars.map(_.lastModified()).max
            val invalidatedSources = files.filter(_.lastModified() >= lastPublicationTime)
            if (invalidatedSources.isEmpty) {
              Def.task(logger.info(s"$P Skip publish for `$projectName`."))
            } else {
              Def.task {
                val onlySources = invalidatedSources
                  .filter(_.isFile)
                  .map(f => baseDirectory.relativize(f.toPath).toString)
                val allChanges = onlySources.mkString("\n\t-> ", "\n\t-> ", "\n")
                logger.warn(s"$P Changes detected in $projectName: $allChanges")
                publishLocalWrapper(moduleID, fasterIvyConfig, true).value
              }
            }
          }
      }
    }
}

def publishLocalWrapper(moduleID: ModuleID,
                        ivyConfiguration: InlineIvyConfiguration,
                        overwrite: Boolean): Def.Initialize[Task[Unit]] = {
  import sbt.internal.librarymanagement._
  import sbt.librarymanagement.{ ModuleSettings, PublishConfiguration }
  Def.task {
    val logger = Keys.streams.value.log

    def publishLocal(moduleSettings: ModuleSettings, config: PublishConfiguration): Unit = {
      val ivy = new IvySbt(ivyConfiguration)
      val module = new ivy.Module(moduleSettings)
      val correctConfig = config.withOverwrite(overwrite)
      val fastConfig: PublishConfiguration =
        correctConfig.withResolverName(ScriptedResolverId).withChecksums(Vector())
      IvyActions.publish(module, fastConfig, logger)
    }

    val name = Keys.name.value
    val version = moduleID.revision
    logger.warn(s"$P Publishing `$name`, version: '$version'.")

    val moduleSettings = Keys.moduleSettings.value
    val publishConfig = Keys.publishLocalConfiguration.value
    publishLocal(moduleSettings, publishConfig)
  }
}

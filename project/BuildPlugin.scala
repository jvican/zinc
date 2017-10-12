import sbt._
import sbt.Keys._
import com.typesafe.sbt.SbtGit.git._
import bintray.BintrayPlugin.autoImport._
import ch.epfl.scala.sbt.release.ReleaseEarlyPlugin.autoImport._
import com.lucidchart.sbt.scalafmt.ScalafmtCorePlugin.autoImport._
import com.lucidchart.sbt.scalafmt.ScalafmtSbtPlugin.autoImport.Sbt
import com.typesafe.tools.mima.plugin.MimaKeys._

object BuildPlugin extends AutoPlugin {
  import sbt.plugins.JvmPlugin
  import com.typesafe.sbt.GitPlugin
  import com.typesafe.tools.mima.plugin.MimaPlugin
  import ch.epfl.scala.sbt.release.ReleaseEarlyPlugin
  import com.lucidchart.sbt.scalafmt.ScalafmtCorePlugin
  override def requires =
    JvmPlugin && ScalafmtCorePlugin && GitPlugin && ReleaseEarlyPlugin && MimaPlugin

  override def trigger = allRequirements
  val autoImport = BuildAutoImported

  override def projectSettings: Seq[Def.Setting[_]] = BuildImplementation.projectSettings
  override def buildSettings: Seq[Def.Setting[_]] = BuildImplementation.buildSettings
}

trait BuildKeys {
  val tearDownBenchmarkResources: TaskKey[Unit] = taskKey[Unit]("Remove benchmark resources.")
  val scriptedPublishAll = taskKey[Unit]("Publishes all the Zinc artifacts for scripted")
  val cleanSbtBridge: TaskKey[Unit] = taskKey[Unit]("Cleans the sbt bridge.")
  val zincPublishLocal: TaskKey[Unit] =
    taskKey[Unit]("Publishes Zinc artifacts to a alternative local cache.")
}

object BuildAutoImported extends BuildKeys {
  import BuildImplementation.{ BuildDefaults, BuildResolvers }

  val baseVersion: String = "1.1.0-SNAPSHOT"
  val internalPath: File = file("internal")
  val bridgeScalaVersions: List[String] =
    List(Dependencies.scala212, Dependencies.scala211, Dependencies.scala210)

  val ZincGitHomepage: URL = url("https://github.com/sbt/zinc")
  val ScalaCenterMaintainer: Developer =
    Developer("jvican", "Jorge Vicente Cantero", "@jvican", url("https://github.com/jvican"))

  // Defines the constants for the alternative publishing
  val ZincAlternativeCacheName = "alternative-local"
  val ZincAlternativeCacheDir: File = file(sys.props("user.home") + "/.ivy2/zinc-alternative")

  // Defines several settings that are exposed to the projects definition in build.sbt
  private[this] val noPublishSettings: Seq[Def.Setting[_]] = BuildDefaults.noPublishSettings
  def noPublish(p: Project): Project = p.settings(noPublishSettings)

  // Sets up mima settings for modules that have to be binary compatible with Zinc 1.0.0
  val mimaSettings: Seq[Def.Setting[_]] =
    List(mimaPreviousArtifacts := BuildDefaults.zincPreviousArtifacts.value)
  val adaptOptionsForOldScalaVersions: Seq[Def.Setting[_]] =
    List(scalacOptions := BuildDefaults.zincScalacOptionsRedefinition.value)
  val zincPublishLocalSettings: Seq[Def.Setting[_]] = List(
    resolvers += BuildResolvers.AlternativeLocalResolver,
    zincPublishLocal := BuildDefaults.zincPublishLocal.value,
  )

  val benchmarksTestDir = sbt.IO.createTemporaryDirectory
  def inCompileAndTest(ss: Def.Setting[_]*): Seq[Def.Setting[_]] =
    List(Compile, Test).flatMap(sbt.inConfig(_)(ss))
}

object BuildImplementation {
  val buildSettings: Seq[Def.Setting[_]] = List(
    Scripted.scriptedBufferLog := true,
    baseVersion := BuildAutoImported.baseVersion,
    gitUncommittedChanges := BuildDefaults.gitUncommitedChanges.value,
    bintrayPackage := "zinc",
    scalafmtOnCompile := true,
    scalafmtVersion := "1.2.0",
    scalafmtOnCompile in Sbt := false,
    releaseEarlyWith := BintrayPublisher,
    description := "Incremental compiler of Scala",
    // The rest of the sbt developers come from the Sbt Houserules plugin
    developers += BuildAutoImported.ScalaCenterMaintainer,
    homepage := Some(BuildAutoImported.ZincGitHomepage),
    version := {
      val previous = Keys.version.value
      if (previous.contains("-SNAPSHOT")) baseVersion.value else previous
    },
  )

  val projectSettings: Seq[Def.Setting[_]] = List(
    // publishArtifact in packageDoc := false,
    // concurrentRestrictions in Global += Util.testExclusiveRestriction,
    scalaVersion := Dependencies.scala212,
    resolvers ++= BuildResolvers.all,
    resolvers ~= BuildResolvers.removeRepeatedResolvers,
    testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-w", "1"),
    javacOptions in Compile ++= Seq("-Xlint", "-Xlint:-serial"),
    crossScalaVersions := Seq(Dependencies.scala211, Dependencies.scala212),
    publishArtifact in Test := false,
    scalacOptions += "-YdisableFlatCpCaching"
  )

  object BuildResolvers {
    import sbt.MavenRepository
    val TypesafeReleases: Resolver = Resolver.typesafeIvyRepo("releases")
    val SonatypeSnapshots: Resolver = Resolver.sonatypeRepo("snapshots")
    val BintrayMavenReleases: Resolver =
      MavenRepository("bintray-sbt-maven-releases", "https://dl.bintray.com/sbt/maven-releases/")
    val BintraySbtIvySnapshots: Resolver =
      Resolver.url("bintray-sbt-ivy-snapshots",
                   new URL("https://dl.bintray.com/sbt/ivy-snapshots/"))(Resolver.ivyStylePatterns)
    val all: List[Resolver] =
      List(TypesafeReleases, SonatypeSnapshots, BintrayMavenReleases, BintraySbtIvySnapshots)

    import BuildAutoImported.{ ZincAlternativeCacheName, ZincAlternativeCacheDir }
    val AlternativeLocalResolver: Resolver =
      Resolver.file(ZincAlternativeCacheName, ZincAlternativeCacheDir)(Resolver.ivyStylePatterns)

    // Naive way of implementing a filter to remove repeated resolvers.
    def removeRepeatedResolvers(rs: Seq[Resolver]): Seq[Resolver] = rs.distinct
  }

  object BuildCommands {
    import sbt.{ Command, State }
    import BuildAutoImported.bridgeScalaVersions
    def crossTestBridges(bridge: Project): Command = {
      Command.command("crossTestBridges") { (state: State) =>
        (bridgeScalaVersions.flatMap { (bridgeVersion: String) =>
          // Note the ! here. You need this so compilerInterface gets forced to the scalaVersion
          s"++ $bridgeVersion!" :: s"${bridge.id}/test" :: Nil
        }) ::: (s"++ ${Dependencies.scala212}!" :: state)
      }
    }

    def publishBridgesAndSet(bridge: Project, interface: Project, apiInfo: Project): Command = {
      Command.args("publishBridgesAndSet", "<version>") { (state, args) =>
        require(args.nonEmpty, "Missing Scala version argument.")
        val userScalaVersion = args.mkString("")
        s"${interface.id}/publishLocal" :: bridgeScalaVersions.flatMap { (v: String) =>
          s"++ $v!" :: s"${apiInfo.id}/publishLocal" :: s"${bridge.id}/publishLocal" :: Nil
        } ::: s"++ $userScalaVersion!" :: state
      }
    }

    def publishBridgesAndTest(bridge: Project, interface: Project, apiInfo: Project): Command = {
      Command.args("publishBridgesAndTest", "<version>") { (state, args) =>
        require(args.nonEmpty, "Missing arguments to publishBridgesAndTest.")
        val version = args mkString ""
        val bridgeCommands: List[String] = bridgeScalaVersions.flatMap { (v: String) =>
          s"++ $v" :: s"${apiInfo.id}/publishLocal" :: s"${bridge.id}/publishLocal" :: Nil
        }
        s"${interface.id}/publishLocal" ::
          bridgeCommands :::
          s"++ $version" ::
          s"zincRoot/scalaVersion" ::
          s"zincRoot/test" ::
          s"zincRoot/scripted" ::
          state
      }
    }

    def runBenchmarks(benchmarkProject: Project): Command = {
      val dirPath = BuildAutoImported.benchmarksTestDir.getAbsolutePath
      val projectId = benchmarkProject.id
      val runPreSetup = s"$projectId/run $dirPath"
      val runBenchmark = s"$projectId/jmh:run -p _tempDir=$dirPath -prof gc"
      val tearDownResources = s"$projectId/tearDownBenchmarkResources"
      Command.command("runBenchmarks")(st => runPreSetup :: runBenchmark :: tearDownResources :: st)
    }

    def all(bridge: Project, interface: Project, apiInfo: Project, bench: Project): Seq[Command] = {
      val crossTest = crossTestBridges(bridge)
      val publishBridges = publishBridgesAndSet(bridge, interface, apiInfo)
      val publishBridgesTest = publishBridgesAndTest(bridge, interface, apiInfo)
      val runBench = runBenchmarks(bench)
      List(crossTest, publishBridges, publishBridgesTest, runBench)
    }
  }

  object BuildDefaults {
    import BuildAutoImported.{ ZincAlternativeCacheName, ZincAlternativeCacheDir }
    import sbt.{ Task, State, fileToRichFile, file, File, IO }
    private[this] val statusCommands = List(
      List("diff-index", "--cached", "HEAD"),
      List("diff-index", "HEAD"),
      List("diff-files"),
      List("ls-files", "--exclude-standard", "--others")
    )

    // https://github.com/sbt/sbt-git/issues/109
    val gitUncommitedChanges: Def.Initialize[Boolean] = Def.setting {
      // Workaround from https://github.com/sbt/sbt-git/issues/92#issuecomment-161853239
      val dir = baseDirectory.value
      // can't use git.runner.value because it's a task
      val runner = com.typesafe.sbt.git.ConsoleGitRunner
      // sbt/zinc#334 Seemingly "git status" resets some stale metadata.
      runner("status")(dir, com.typesafe.sbt.git.NullLogger)
      val uncommittedChanges = statusCommands.flatMap { c =>
        val res = runner(c: _*)(dir, com.typesafe.sbt.git.NullLogger)
        if (res.isEmpty) Nil else List(c -> res)
      }
      val logger = sLog.value
      val areUncommited = uncommittedChanges.nonEmpty
      if (areUncommited) {
        uncommittedChanges.foreach {
          case (cmd, res) =>
            logger.debug(s"""Uncommitted changes found via "${cmd.mkString(" ")}":\n${res}""")
        }
      }
      areUncommited
    }

    import sbt.{ CrossVersion, ModuleID, stringToOrganization }
    val zincPreviousArtifacts: Def.Initialize[Set[ModuleID]] = Def.setting {
      val zincModule = (organization.value % moduleName.value % "1.0.0")
        .cross(if (crossPaths.value) CrossVersion.binary else CrossVersion.disabled)
      Set(zincModule)
    }

    private[this] val toFilterInOldScala: Set[String] = Set(
      "-Xfatal-warnings",
      "-deprecation",
      "-Ywarn-unused",
      "-Ywarn-unused-import",
      "-YdisableFlatCpCaching"
    )

    val zincScalacOptionsRedefinition: Def.Initialize[Task[Seq[String]]] = Def.task {
      val old = scalacOptions.value
      scalaBinaryVersion.value match {
        case v if v == "2.12" || v == "2.13" => old
        case _                               => old.filterNot(toFilterInOldScala)
      }
    }

    val zincPublishLocal: Def.Initialize[Task[Unit]] = Def.task {
      import sbt.internal.librarymanagement._
      val logger = streams.value.log
      val config = (publishLocalConfiguration).value
      val ivy = new IvySbt((ivyConfiguration.value))
      val modSettings = (moduleSettings).value
      val module = new ivy.Module(modSettings)
      val newConfig = config.withResolverName(ZincAlternativeCacheName).withOverwrite(false)
      logger.info(s"Publishing $module to local repo: $ZincAlternativeCacheName")
      Set(IvyActions.publish(module, newConfig, logger))
    }

    val noPublishSettings: Seq[Def.Setting[_]] = List(
      publish := {},
      publishLocal := {},
      publishArtifact in Compile := false,
      publishArtifact in Test := false,
      publishArtifact := false,
      skip in publish := true,
    )

    private[this] def wrapIn(color: String, content: String): String = {
      import sbt.internal.util.ConsoleAppender
      if (!ConsoleAppender.formatEnabledInEnv) content
      else color + content + scala.Console.RESET
    }

    val cleanSbtBridge: Def.Initialize[Task[Unit]] = Def.task {
      val sbtV = sbtVersion.value
      val sbtOrg = "org.scala-sbt"
      val sbtScalaVersion = "2.10.6"
      val bridgeVersion = version.value
      val scalaV = scalaVersion.value

      // Assumes that JDK version is the same than the one that publishes the bridge
      val classVersion = System.getProperty("java.class.version")

      val home = System.getProperty("user.home")
      val org = organization.value
      val artifact = moduleName.value
      val artifactName = s"$org-$artifact-$bridgeVersion-bin_${scalaV}__$classVersion"

      val targetsToDelete = List(
        // We cannot use the target key, it's not scoped in `ThisBuild` nor `Global`.
        (baseDirectory in sbt.ThisBuild).value / "target" / "zinc-components",
        file(home) / ".ivy2/cache" / sbtOrg / artifactName,
        file(home) / ".ivy2/local" / sbtOrg / artifactName,
        file(home) / ".sbt/boot" / s"scala-$sbtScalaVersion" / sbtOrg / "sbt" / sbtV / artifactName
      )

      val logger = streams.value.log
      logger.info(wrapIn(scala.Console.BOLD, "Cleaning stale compiler bridges:"))
      targetsToDelete.foreach { target =>
        IO.delete(target)
        logger.info(s"${wrapIn(scala.Console.GREEN, "  ✓ ")}${target.getAbsolutePath}")
      }
    }

    private[this] val scalaPartialVersion =
      Def.setting(CrossVersion.partialVersion(scalaVersion.value))
    val handleScalaSpecificSources: Def.Initialize[List[File]] = Def.setting {
      val source = scalaSource.value
      scalaPartialVersion.value.collect {
        case (2, y) if y == 10 => new File(source.getPath + "_2.10")
        case (2, y) if y >= 11 => new File(source.getPath + "_2.11+")
      }.toList
    }

    import sbt.{ InputTask }
    import Scripted.{ scriptedSource, scriptedParser, scriptedBufferLog, scriptedPrescripted }
    def zincScripted(bridgeRef: Project,
                     interfaceRef: Project,
                     scriptedRef: Project): Def.Initialize[InputTask[Unit]] = Def.inputTask {
      val result = scriptedSource(dir => (s: State) => scriptedParser(dir)).parsed
      // We first publish all the zinc modules
      BuildAutoImported.scriptedPublishAll.value

      val source = scriptedSource.value
      val logged = scriptedBufferLog.value
      val hook = scriptedPrescripted.value

      // Publish the interface and the bridge for scripted to resolve them correctly
      (BuildAutoImported.zincPublishLocal in interfaceRef).value
      (BuildAutoImported.zincPublishLocal in bridgeRef).value

      val scriptedClasspath = (fullClasspath in scriptedRef in Test).value
      val instance = (scalaInstance in scriptedRef).value
      Scripted.doScripted(scriptedClasspath, instance, source, result, logged, hook)
    }

    def zincOnlyScripted(scriptedRef: Project): Def.Initialize[InputTask[Unit]] = Def.inputTask {
      val result = scriptedSource(dir => (s: State) => scriptedParser(dir)).parsed
      val scriptedClasspath = (fullClasspath in scriptedRef in Test).value
      val instance = (scalaInstance in scriptedRef).value
      val source = scriptedSource.value
      val logged = scriptedBufferLog.value
      val hook = scriptedPrescripted.value
      Scripted.doScripted(scriptedClasspath, instance, source, result, logged, hook)
    }

    private[this] val ZincAlternativeResolverPlugin = s"""
       |import sbt._
       |import Keys._
       |
       |object AddResolverPlugin extends AutoPlugin {
       |  override def requires = sbt.plugins.JvmPlugin
       |  override def trigger = allRequirements
       |
       |  override lazy val projectSettings = Seq(resolvers += alternativeLocalResolver)
       |  lazy val alternativeLocalResolver = Resolver.file("$ZincAlternativeCacheName", file("${ZincAlternativeCacheDir.getAbsolutePath}"))(Resolver.ivyStylePatterns)
       |}
       |""".stripMargin

    def addSbtAlternateResolver(scriptedRoot: File): Unit = {
      val resolver = scriptedRoot / "project" / "AddResolverPlugin.scala"
      if (!resolver.exists) IO.write(resolver, ZincAlternativeResolverPlugin)
      else ()
    }

    val tearDownBenchmarkResources: Def.Initialize[Task[Unit]] =
      Def.task(IO.delete(BuildAutoImported.benchmarksTestDir))
  }
}

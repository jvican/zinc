import sbt.{ AutoPlugin, Compile, Def, Keys, Resolver, Test, TestFrameworks, Tests, URL }

object BuildPlugin extends AutoPlugin {
  override def requires = sbt.plugins.JvmPlugin
  override def trigger = allRequirements
  val autoImport = BuildKeys

  override def projectSettings: Seq[Def.Setting[_]] = BuildImplementation.projectSettings
  override def buildSettings: Seq[Def.Setting[_]] = BuildImplementation.buildSettings
  override def globalSettings: Seq[Def.Setting[_]] = BuildImplementation.globalSettings
}

object BuildKeys {
  val bridgeScalaVersions: List[String] =
    List(Dependencies.scala212, Dependencies.scala211, Dependencies.scala210)

  // Ids that we will use to name our projects in build.sbt
  val CompilerInterfaceId = "compiler-interface"
  val CompilerBridgeId = "compiler-bridge"
  val ZincApiInfoId = "zinc-apiinfo"

  private[this] val toFilterInOldScala: Set[String] = Set(
    "-Xfatal-warnings",
    "-deprecation",
    "-Ywarn-unused",
    "-Ywarn-unused-import",
    "-YdisableFlatCpCaching"
  )

  def adaptOptionsForOldScalaVersions: Seq[Def.Setting[_]] = Seq(
    Keys.scalacOptions := {
      val old = Keys.scalacOptions.value
      Keys.scalaBinaryVersion.value match {
        case v if v == "2.12" || v == "2.13" => old
        case _                               => old.filterNot(toFilterInOldScala)
      }
    }
  )
}

object BuildImplementation {
  val buildSettings: Seq[Def.Setting[_]] = Nil
  val globalSettings: Seq[Def.Setting[_]] = List(
    Keys.commands ++= BuildCommands.all
  )

  val projectSettings: Seq[Def.Setting[_]] = List(
    // publishArtifact in packageDoc := false,
    // concurrentRestrictions in Global += Util.testExclusiveRestriction,
    Keys.scalaVersion := Dependencies.scala212,
    Keys.resolvers ++= BuildResolvers.all,
    Keys.resolvers ~= BuildResolvers.removeRepeatedResolvers,
    Keys.testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-w", "1"),
    Keys.javacOptions in Compile ++= Seq("-Xlint", "-Xlint:-serial"),
    Keys.crossScalaVersions := Seq(Dependencies.scala211, Dependencies.scala212),
    Keys.publishArtifact in Test := false,
    Keys.scalacOptions += "-YdisableFlatCpCaching"
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

    // Naive way of implementing a filter to remove repeated resolvers.
    def removeRepeatedResolvers(rs: Seq[Resolver]): Seq[Resolver] = rs.toSet.toVector
  }

  object BuildCommands {
    import sbt.{ Command, State }
    import BuildKeys.{ CompilerBridgeId, ZincApiInfoId, CompilerInterfaceId, bridgeScalaVersions }
    val crossTestBridges: Command = {
      Command.command("crossTestBridges") { (state: State) =>
        (bridgeScalaVersions.flatMap { (bridgeVersion: String) =>
          // Note the ! here. You need this so compilerInterface gets forced to the scalaVersion
          s"++ $bridgeVersion!" :: s"$CompilerBridgeId/test" :: Nil
        }) ::: (s"++ ${Dependencies.scala212}!" :: state)
      }
    }

    val publishBridgesAndSet: Command = {
      Command.args("publishBridgesAndSet", "<version>") { (state, args) =>
        require(args.nonEmpty, "Missing Scala version argument.")
        val userScalaVersion = args.mkString("")
        s"$CompilerInterfaceId/publishLocal" :: bridgeScalaVersions.flatMap { (v: String) =>
          s"++ $v!" :: s"$ZincApiInfoId/publishLocal" :: s"$CompilerBridgeId/publishLocal" :: Nil
        } ::: s"++ $userScalaVersion!" :: state
      }
    }

    val publishBridgesAndTest: Command = Command.args("publishBridgesAndTest", "<version>") {
      (state, args) =>
        require(args.nonEmpty, "Missing arguments to publishBridgesAndTest.")
        val version = args mkString ""
        val bridgeCommands: List[String] = bridgeScalaVersions.flatMap { (v: String) =>
          s"++ $v" :: s"$ZincApiInfoId/publishLocal" :: s"$CompilerBridgeId/publishLocal" :: Nil
        }
        s"$CompilerInterfaceId/publishLocal" ::
          bridgeCommands :::
          s"++ $version" ::
          s"zincRoot/scalaVersion" ::
          s"zincRoot/test" ::
          s"zincRoot/scripted" ::
          state
    }
    val all: List[Command] = List(crossTestBridges, publishBridgesAndSet, publishBridgesAndTest)
  }
}

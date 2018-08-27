package bloop.sbt.integrations

import bintray.BintrayKeys
import ch.epfl.scala.sbt.release.{ReleaseEarlyPlugin, AutoImported => ReleaseEarlyNamespace}
import com.typesafe.sbt.SbtPgp
import com.typesafe.sbt.SbtPgp.autoImport.PgpKeys
import sbt.{AutoPlugin, Compile, Def, Keys, file}
import sbtdynver.{DynVerPlugin, GitDescribeOutput}
import sbthouserules.HouseRulesPlugin

object BloopPlugin extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = ReleaseEarlyPlugin && SbtPgp && HouseRulesPlugin && DynVerPlugin
  val autoImport = BloopKeys

  override def globalSettings: Seq[Def.Setting[_]] = BloopPluginImplementation.globalSettings
  override def buildSettings: Seq[Def.Setting[_]] = BloopPluginImplementation.buildSettings
  override def projectSettings: Seq[Def.Setting[_]] = BloopPluginImplementation.projectSettings
}

object BloopKeys {}

object BloopPluginImplementation {
  val globalSettings: Seq[Def.Setting[_]] = List()
  val buildSettings: Seq[Def.Setting[_]] = List(
    Keys.organization := "ch.epfl.scala",
    BintrayKeys.bintrayOrganization := Some("scalacenter"),
    Keys.startYear := Some(2017),
    Keys.autoAPIMappings := true,
    Keys.publishMavenStyle := true,
    ReleaseEarlyNamespace.releaseEarlyWith := ReleaseEarlyNamespace.SonatypePublisher,
    PgpKeys.pgpPublicRing := {
      if (Keys.insideCI.value) file("/drone/.gnupg/pubring.asc")
      else PgpKeys.pgpPublicRing.value
    },
    PgpKeys.pgpSecretRing := {
      if (Keys.insideCI.value) file("/drone/.gnupg/secring.asc")
      else PgpKeys.pgpPublicRing.value
    }
  )

  import DynVerPlugin.{autoImport => DynVerKeys}
  val projectSettings: Seq[Def.Setting[_]] = List(
    BintrayKeys.bintrayRepository := "releases",
    BintrayKeys.bintrayPackage := "zinc",
    // Add some metadata that is useful to see in every on-merge bintray release
    BintrayKeys.bintrayPackageLabels := List("incremental", "compilation", "scala", "java"),
    ReleaseEarlyNamespace.releaseEarlyPublish := PgpKeys.publishSigned.value,
    Keys.isSnapshot := {
      val output = DynVerKeys.dynverGitDescribeOutput.value
      val version = Keys.version.value
      !BloopDefaults.publishDocAndSourceArtifact(output, version)
    },
    Keys.publishArtifact in (Compile, Keys.packageDoc) := {
      val output = DynVerKeys.dynverGitDescribeOutput.value
      val version = Keys.version.value
      BloopDefaults.publishDocAndSourceArtifact(output, version)
    },
    Keys.publishArtifact in (Compile, Keys.packageSrc) := {
      val output = DynVerKeys.dynverGitDescribeOutput.value
      val version = Keys.version.value
      BloopDefaults.publishDocAndSourceArtifact(output, version)
    },
  )

  object BloopDefaults {

    /**
     * This setting figures out whether the version is a snapshot or not and configures
     * the source and doc artifacts that are published by the build.
     *
     * Snapshot is a term with no clear definition. In this code, a snapshot is a revision
     * that is dirty, e.g. has time metadata in its representation. In those cases, the
     * build will not publish doc and source artifacts by any of the publishing actions.
     */
    def publishDocAndSourceArtifact(info: Option[GitDescribeOutput], version: String): Boolean = {
      val isStable = info.map(_.dirtySuffix.value.isEmpty)
      !isStable.map(stable => !stable || version.endsWith("-SNAPSHOT")).getOrElse(false)
    }
  }
}

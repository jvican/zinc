import sbt._
import Keys._

import ch.epfl.scala.sbt.release.ReleaseEarlyPlugin.autoImport._
import sbtdynver.DynVerPlugin.autoImport._
import sbtdynver.DynVer

object OurStuff {
  lazy val ourDynVerInstance = settingKey[DynVer]("")
  lazy val scriptedPublish = taskKey[Unit]("Publishes all the Zinc artifacts for scripted")
  lazy val cachedPublishLocal = taskKey[Unit]("Publishes a project if it hasn't been published before.")

  lazy val extraBuildSettings = Seq(
    releaseEarlyWith := BintrayPublisher,
    publishArtifact in (Compile, Keys.packageDoc) :=
      publishDocAndSourceArtifact.value,
      publishArtifact in (Compile, Keys.packageSrc) :=
        publishDocAndSourceArtifact.value,
      ourDynVerInstance := sbtdynver.DynVer(Some(baseDirectory.value)),
      dynver := ourDynVerInstance.value.version(new java.util.Date),
      dynverGitDescribeOutput :=
        ourDynVerInstance.value.getGitDescribeOutput(dynverCurrentDate.value)
  )

  lazy val extraCommonSettings = Seq(
    cachedPublishLocal := cachedPublishLocalImpl.value,
    resolvers += ScriptedResolver
  )

  lazy val extraRootSettings = Seq(
    scriptedPublish := cachedPublishLocalImpl.all(ScopeFilter(inAnyProject)).value
  )

  lazy val extraCompilerBridgeSettings = Seq(
    cachedPublishLocal := cachedPublishLocal
      .dependsOn(cachedPublishLocal.in(LocalProject("zincApiInfo")))
      .dependsOn(cachedPublishLocal.in(LocalProject("compilerInterface")))
      .value,
    // Make sure that the sources are published for the bridge because we need them to compile it
    publishArtifact in (Compile, packageSrc) := true,
  )

  // Defines a resolver that is used to publish only for local testing via scripted
  lazy val ScriptedResolverId = "zinc-scripted-local"
  lazy val ScriptedResolveCacheDir: File = file(sys.props("user.dir") + s"/.ivy2/$ScriptedResolverId")
  lazy val ScriptedResolver: Resolver =
    Resolver.file(ScriptedResolverId, ScriptedResolveCacheDir)(Resolver.ivyStylePatterns)

  /**
   * This setting figures out whether the version is a snapshot or not and configures
   * the source and doc artifacts that are published by the build.
   *
   * Snapshot is a term with no clear definition. In this code, a snapshot is a revision
   * that has either build or time metadata in its representation. In those cases, the
   * build will not publish doc and source artifacts by any of the publishing actions.
   */
  lazy val publishDocAndSourceArtifact: Def.Initialize[Boolean] = Def.setting {
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
}

package sbt.zinc

import java.net.URLClassLoader
import java.lang.reflect

import sbt._
import Keys._
import sbt.compiler.{AnalyzingCompiler, ComponentCompiler, RawCompiler}
import sbt.inc.Analysis
import sbt.plugins.JvmPlugin

trait ZincCompileKeys {
  val zincCompile = taskKey[Unit]("Compile your project with Zinc.")
  val zincInternalJar = taskKey[File]("Location of mega jar for Zinc 1.0.")
  val zincEnabled = settingKey[Boolean]("Enable zinc incremental compilation.")
  val zincCompiledBridge = taskKey[File]("Get the binaries of the bridge.")
  val zincBridgeSources = settingKey[ModuleID]("Module of Zinc bridge sources")
  val zincDebug = settingKey[Boolean]("Flag to enable debug option.")
}

object ZincCompilePlugin
    extends AutoPlugin
    with ZincCompileKeys
    with SbtUtils
    with SbtStubs
    with ReflectionUtils {

  object autoImport extends ZincCompileKeys

  override def trigger: PluginTrigger = AllRequirements
  override def requires: Plugins = JvmPlugin

  val zincStub211 = stubProjectFor("2.11.8", ZincVersion)
  val zincStub212 = stubProjectFor("2.12.1", ZincVersion)

  override def extraProjects: Seq[Project] = Seq(zincStub211, zincStub212)

  private lazy val rootLoader = {
    def parent(loader: ClassLoader): ClassLoader = {
      val p = loader.getParent
      if (p eq null) loader else parent(p)
    }
    val systemLoader = ClassLoader.getSystemClassLoader
    if (systemLoader ne null) parent(systemLoader)
    else parent(getClass.getClassLoader)
  }

  val BridgeId = "compiler-bridge_2.12"
  val InterfaceId = "compiler-interface"

  private var firstIteration: Boolean = true
  private var cachedCompiledBridge: File = _
  private var cachedCompilerVersion: String = _

  private var previousZincJar: File = _
  private var cachedLoader: URLClassLoader = _
  private var cachedZincClazz: Class[_] = _
  private var cachedZincInstance: Any = _
  private var cachedEntrypoint: reflect.Method = _

  override def projectSettings: Seq[Def.Setting[_]] = {
    Seq(
      zincInternalJar := getZincMegaJar(zincStub211, zincStub212).value,
      zincBridgeSources := {
        val module = ZincGroupId % BridgeId % ZincVersion % "component"
        module.sources()
      },
      zincCompiledBridge := {
        val currentScala = scalaBinaryVersion.value
        if (cachedCompiledBridge == null ||
            cachedCompilerVersion != currentScala) {
          cachedCompilerVersion = currentScala
          cachedCompiledBridge = getCompiledBridge.value
        }
        cachedCompiledBridge
      },
      zincCompile := {
        val st = streams.value
        val logger = st.log

        // Introduce yourself Zinc, don't be shy!
        if (firstIteration) {
          firstIteration = false
          logger.info(CoolHeader)
        }

        // If resolved jar is different, populate again.
        val zincJar = zincInternalJar.value
        if (previousZincJar != zincJar) {
          val jars = Path.toURLs(List(zincJar))
          cachedLoader = new URLClassLoader(jars, rootLoader)
          cachedZincClazz = Class.forName(ZincProxyFQN, false, cachedLoader)
          cachedZincInstance = cachedZincClazz.newInstance()
          cachedEntrypoint = getProxyMethod(cachedZincClazz)
          previousZincJar = zincJar
        }

        val version = scalaVersion.value
        val analysisFilename = (compileAnalysisFilename in Compile).value
        val cacheDir = st.cacheDirectory / name.value / analysisFilename
        val bridge = zincCompiledBridge.value
        val classpath = (dependencyClasspath in Compile).value

        val sourceFiles = (sources in Compile).value.toArray
        val entries = classpath.map(_.data).toArray
        val compilationDir = (classDirectory in Compile).value
        val scalaOptions = (scalacOptions in Compile).value.toArray
        val javaOptions = (javacOptions in Compile).value.toArray

        // Invoke our beauty reflectively, as it's meant to be
        cachedEntrypoint.invoke(cachedZincInstance,
                                version,
                                bridge,
                                cacheDir,
                                entries,
                                sourceFiles,
                                compilationDir,
                                scalaOptions,
                                javaOptions)
      }
    )
  }
}

trait ReflectionUtils {
  protected val ZincProxyFQN = "sbt.zinc.ZincProxy"
  protected val RunMethod = "run"
  protected val CompilerBridgeMethod = "compileBridge"

  def getProxyMethod(clazz: Class[_]): reflect.Method = {
    clazz.getDeclaredMethod(
      RunMethod,
      classOf[String],
      classOf[File],
      classOf[File],
      classOf[Array[File]],
      classOf[Array[File]],
      classOf[File],
      classOf[Array[String]],
      classOf[Array[String]]
    )
  }

  def getCompileMethod(clazz: Class[_]): reflect.Method = {
    clazz.getDeclaredMethod(CompilerBridgeMethod, classOf[Object])
  }
}

/**
  * Define sbt utils for the plugin.
  *
  * <a href="https://github.com/scalacenter/scalafix/blob/master/scalafix-sbt/src/main/scala/scalafix/sbt/ScalafixPlugin.scala">Origin</a>.
  */
trait SbtUtils { self: SbtStubs with ZincCompileKeys =>
  protected val Version = "2\\.(\\d\\d)\\..*".r
  protected val ZincArtifactName = "zinc-sbt-proxy"
  protected val ZincGroupId = "org.scala-sbt"
  protected val ZincVersion = "1.0.0-X9-SNAPSHOT"

  private def failedResolution(report: UpdateReport) =
    s"Unable to resolve the Zinc proxy: $report"

  protected def getJar(report: UpdateReport, zincVersion: String): File = {
    val jarVersion = s".*${ZincArtifactName}_2.1[12](-$zincVersion)?.jar$$"
    val jar = report.allFiles.find(f => f.getAbsolutePath.matches(jarVersion))
    jar.getOrElse(throw new IllegalStateException(failedResolution(report)))
  }

  protected def stubProjectFor(version: String, zincVersion: String): Project = {
    val Version(id) = version
    Project(id = s"zinc-$id", base = file(s"project/zinc/$id"))
      .settings(
        description := "Used to resolve zinc jars and cache them.",
        publishLocal := {},
        publish := {},
        publishArtifact := false,
        // necessary to support intransitive dependencies.
        publishMavenStyle := false,
        scalaVersion := version,
        // remove injected dependencies from random sbt plugins.
        libraryDependencies := Nil,
        libraryDependencies +=
          (ZincGroupId %% ZincArtifactName % zincVersion).intransitive()
      )
  }

  protected def getZincMegaJar(stubFor211: Project, stubFor212: Project) = {
    Def.taskDyn[File] {
      val updateAction = {
        Keys.scalaVersion.value match {
          case Version("11") => Keys.update in stubFor211
          case Version("12") => Keys.update in stubFor212
          case _ => sys.error("Only Scala 2.11 or 2.12 are supported.")
        }
      }
      Def.task(getJar(updateAction.value, ZincVersion))
    }
  }

  protected def getCompiledBridge: Def.Initialize[Task[File]] = {
    (zincBridgeSources,
     zincInternalJar,
     scalaInstance,
     bootIvyConfiguration,
     appConfiguration,
     streams).map {
      case (src, jar, instance, ivy, app, sts) =>
        val log = sts.log
        val interface = List(jar)
        val components = app.provider.components
        val launcher = app.provider.scalaProvider.launcher
        val ivyHome = Option(launcher.ivyHome)
        val lock = launcher.globalLock
        val manager = new ComponentManager(lock, components, ivyHome, log)
        val raw = new RawCompiler(instance, ClasspathOptions.auto, log)
        val compiler =
          new ModuleCompiler(raw, manager, ivy, src, interface, log)
        log.debug(s"Getting the compiler sources for ${instance.version}")
        compiler.getCompiledBinaries
    }
  }

  val y = fansi.Color.LightGreen
  val CoolHeader: String =
    s"""
       |
       |${y("=============================================================")}
       |${y("= Welcome to experimental Zinc 1.0 incremental compilation! =")}
       |${y("=============================================================")}
       |
       |${y("This work is a joint effort from the Scala Center and Lightbend.")}
       |
       |${y("Please, do file bugs here: https://github.com/sbt/zinc/issues/new.")}
       |${y("Enable the debug setting to give as much information as possible.")}
    """.stripMargin

}

trait SbtStubs {
  class ModuleCompiler(compiler: RawCompiler,
                       manager: ComponentManager,
                       ivyConfiguration: IvyConfiguration,
                       bridge: ModuleID,
                       classpath: Iterable[File],
                       log: Logger) {

    import ComponentCompiler._
    private val buffered = new BufferedLogger(FullLogger(log))
    private val updateUtil = new UpdateUtil(ivyConfiguration, buffered)

    /**
      * Get the compiled bridge either from cache or compile it.
      *
      * @return File where the compiled binaries of the bridge are.
      */
    def getCompiledBinaries: File = {
      val binID = binaryID(
        s"${bridge.organization}-${bridge.name}-${bridge.revision}")
      manager.file(binID)(new IfMissing.Define(true, compileAndInstall(binID)))
    }

    /** The binary id is of the form:
      * """org.example-compilerbridge-1.0.0-bin_2.11.7__50.0"""
      */
    private def binaryID(id: String): String = {
      val base = id + binSeparator + compiler.scalaInstance.actualVersion
      base + "__" + javaVersion
    }

    import AnalyzingCompiler.{compileSources => compile}
    private def compileAndInstall(binID: String): Unit =
      IO.withTemporaryDirectory { binaryDirectory =>
        val targetJar = new File(binaryDirectory, s"$binID.jar")
        buffered bufferQuietly {
          IO.withTemporaryDirectory { retrieveDirectory =>
            updateUtil.update(updateUtil.getModule(bridge), retrieveDirectory)(
              _.getName endsWith "-sources.jar") match {
              case Some(srcs) =>
                compile(srcs, targetJar, classpath, bridge.name, compiler, log)
                manager.define(binID, Seq(targetJar))

              case None =>
                throw new InvalidComponent(
                  s"Couldn't retrieve source module: $bridge")
            }
          }

        }
      }
  }
}

package sbt.zinc

import java.io.File

import xsbti.Maybe
import xsbti.compile.{ScalaInstance => _, _}
import sbt.inc.BaseCompilerSpec
import sbt.internal.inc._
import sbt.internal.util.{ConsoleLogger, ConsoleOut, MainAppender, ManagedLogger}
import sbt.util.{InterfaceUtil, Level, LogExchange}

import scala.collection.mutable

class ZincProxy extends BaseCompilerSpec with ZincUtils {
  val MaxCompilationErrors = 100

  private var cachedInstance: ScalaInstance = _
  private val analysisMap = mutable.Map.empty[File, Analysis]
  private val compilerCache = CompilerCache.fresh
  var previousResult: PreviousResult = _

  /**
    * Run the magic of the Zinc compiler.
    *
    * @param scalaVersion The Scala version to compile against.
    * @param compiledBridge The bridge of the scala version already compiled.
    * @param cacheDir The directory where incremental compilation is stored.
    */
  def run(scalaVersion: String,
          compiledBridge: File,
          cacheDir: File,
          classpath: Array[File],
          sources: Array[File],
          classDirectory: File,
          scalacOptions: Array[String],
          javacOptions: Array[String]) = {

    cachedInstance = scalaInstance(scalaVersion)
      .asInstanceOf[sbt.internal.inc.ScalaInstance]
    logger.info("Created Scala instance.")
    val compiler = scalaCompiler(cachedInstance, compiledBridge)
    logger.info(s"Created Scala compiler from bridge $compiledBridge.")
    val incremental = new IncrementalCompilerImpl
    val options = ClasspathOptionsUtil.boot
    val compilers = incremental.compilers(cachedInstance, options, None, compiler)

    if (analysisMap.isEmpty)
      analysisMap ++= classpath.map(_ -> Analysis.empty)
    val entryLookup = ZincLookup(analysisMap.get)
    val defaultOptions = IncOptionsUtil.defaultIncOptions()
    val reporter = new LoggerReporter(maxErrors, managedLogger, identity)

    val setup = incremental.setup(entryLookup,
                                  skip = false,
                                  cacheDir,
                                  compilerCache,
                                  defaultOptions,
                                  reporter,
                                  None,
                                  Array())
    val previous =
      if (previousResult != null) previousResult
      else incremental.emptyPreviousResult
    val inputs = incremental.inputs(classpath,
                                    sources,
                                    classDirectory,
                                    scalacOptions,
                                    javacOptions,
                                    MaxCompilationErrors,
                                    Array(),
                                    CompileOrder.Mixed,
                                    compilers,
                                    setup,
                                    previous)
    logger.info("Starting incremental compilation with Zinc 1.0.")
    logger.info(s"> Classpath: ${classpath.mkString(",")}")
    incremental.compile(inputs, logger)
  }
}

trait ZincUtils {
  val logger = ConsoleLogger()

  val managedLogger: ManagedLogger = {
    val console = ConsoleOut.systemOut
    val consoleAppender = MainAppender.defaultScreen(console)
    val loggerName = s"test-zinc-${getClass.hashCode()}"
    val x = LogExchange.logger(loggerName)
    LogExchange.unbindLoggerAppenders(loggerName)
    LogExchange.bindLoggerAppenders(loggerName,
                                    (consoleAppender -> Level.Warn) :: Nil)
    x
  }

  case class ZincLookup(am: File => Option[CompileAnalysis])
      extends PerClasspathEntryLookup {
    override def analysis(classpathEntry: File): Maybe[CompileAnalysis] =
      InterfaceUtil.o2m(am(classpathEntry))

    override def definesClass(classpathEntry: File): DefinesClass =
      Locate.definesClass(classpathEntry)
  }
}

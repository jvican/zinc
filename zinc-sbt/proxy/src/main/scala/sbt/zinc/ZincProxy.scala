package sbt.zinc

import java.io.File

import sbt.inc.BaseCompilerSpec
import sbt.internal.inc.{ScalaInstance => _, _}
import sbt.internal.util.{ConsoleLogger, ConsoleOut, MainAppender, ManagedLogger}
import sbt.util._
import xsbti.Maybe
import xsbti.compile._
import sbt.io.syntax._
import org.apache.logging.log4j.{Level => Log4Level}

import scala.collection.mutable

class ZincProxy extends BaseCompilerSpec with ZincUtils {

  type AnalysisMap = mutable.Map[File, Analysis]
  case class Cached(entryLookup: PerClasspathEntryLookup,
                    store: AnalysisStore,
                    compilers: Compilers) {
    def getAll = (entryLookup, store, compilers)
  }

  private val MaxCompilationErrors = 100
  private val cachedScalas = mutable.Map.empty[String, Cached]
  private val compilerCache = CompilerCache.fresh
  val ClasspathOptions = ClasspathOptionsUtil.boot

  // The incremental compiler implementation is stateless
  private val inc = new IncrementalCompilerImpl
  import inc.{setup => Setup, inputs => Inputs}

  /**
    * Run the magic Zinc compiler.
    *
    * @param scalaVersion The Scala version to compile against.
    * @param compilerJar The compiler jar for creating the scala instnace.
    * @param stdLibraryJar The library jar for creating the scala instance.
    * @param extras The extras of the the scala instance.
    * @param compiledBridge The bridge of the scala version already compiled.
    * @param cacheDir The directory where incremental compilation is stored.
    * @param classpath The compilation classpath.
    * @param sources The source files to be compiled.
    * @param classDirectory The target class directory.
    * @param scalacOptions The scalac options.
    * @param javacOptions The javacOptions
    */
  def run(scalaVersion: String,
          compilerJar: File,
          stdLibraryJar: File,
          extras: Array[File],
          compiledBridge: File,
          cacheDir: File,
          classpath: Array[File],
          sources: Array[File],
          classDirectory: File,
          scalacOptions: Array[String],
          javacOptions: Array[String],
          compilationOrderString: String,
          debug: Boolean) = {

    if (debug) {
      logger.setLevel(Level.Debug)
      LogExchange.loggerConfig(loggerName).setLevel(Log4Level.DEBUG)
    }

    logger.info("Starting to fetch")
    val hitOrMiss = cachedScalas.get(scalaVersion)
    val cached: Cached = {
      if (hitOrMiss.isEmpty) {
        val instance = scalaInstance(compilerJar, stdLibraryJar, extras.toList)
        logger.info("Created Scala instance.")
        val compiler = scalaCompiler(instance, compiledBridge)
        logger.info(s"Created Scala compiler from bridge $compiledBridge.")
        val comps = inc.compilers(instance, ClasspathOptions, None, compiler)
        val cacheFile = cacheDir / "inc_compile.zip"
        val store = AnalysisStore.cached(FileBasedStore(cacheFile))
        val analysisMap = classpath.map(_ -> Analysis.empty).toMap
        val entryLookup = ZincLookup(analysisMap.get)
        val cached = Cached(entryLookup, store, comps)
        cachedScalas += scalaVersion -> cached
        cached
      } else hitOrMiss.get
    }

    // TODO(jvican): Support user-defined incremental options
    val opts = IncOptionsUtil.defaultIncOptions()
    val rlog = new LoggerReporter(maxErrors, managedLogger, identity)
    val (lookup, store, compilers) = cached.getAll
    val lastResult = getLastResult(store)

    val setup =
      Setup(lookup, false, cacheDir, compilerCache, opts, rlog, None, Array())

    val order = parseCompilationOrder(compilationOrderString)
    val compileOptions = new CompileOptions(classpath,
                                            sources,
                                            classDirectory,
                                            scalacOptions,
                                            javacOptions,
                                            MaxCompilationErrors,
                                            InterfaceUtil.f1(identity),
                                            order)

    val sep = "\n  >"
    logger.info("Starting incremental compilation with Zinc 1.0.")
    logger.info(s"Classpath: ${classpath.mkString(sep, sep, sep)}")
    val inputs = Inputs(compileOptions, compilers, setup, lastResult)
    val result = inc.compile(inputs, logger)
    store.set(result.analysis(), result.setup())
  }

  private def parseCompilationOrder(order: String): CompileOrder = {
    order.toLowerCase match {
      case "mixed" => CompileOrder.Mixed
      case "javathenscala" => CompileOrder.JavaThenScala
      case "scalathenjava" => CompileOrder.ScalaThenJava
    }
  }

  private def getLastResult(analysisStore: AnalysisStore): PreviousResult = {
    analysisStore.get() match {
      case Some((prevAnalysis, prevSetup)) =>
        new PreviousResult(Maybe.just[CompileAnalysis](prevAnalysis),
                           Maybe.just[MiniSetup](prevSetup))
      case _ =>
        inc.emptyPreviousResult
    }
  }
}

trait ZincUtils {
  val logger: AbstractLogger = ConsoleLogger()

  val loggerName = s"test-zinc-${getClass.hashCode()}"
  val managedLogger: ManagedLogger = {
    val console = ConsoleOut.systemOut
    val consoleAppender = MainAppender.defaultScreen(console)
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

/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbt

import xsbti.{ AnalysisCallback, Logger, Problem, Reporter }
import xsbti.compile._

import scala.collection.mutable
import Log.{ debug, warn, error }
import java.io.File

final class CompilerInterface {
  def newCompiler(
      options: Array[String],
      output: Output,
      initialLog: Logger,
      initialDelegate: Reporter
  ): CachedCompiler = new CachedCompiler0(options, output, new WeakLog(initialLog, initialDelegate))

  private var store0: IRStore = null
  private var manager0: ClassFileManager = null

  /**
   * Sets the IR store of a compiler run before [[run]] is called.
   *
   * The method is not added to the interface of `CompilerInterface` because
   * we still support JDK 6 (Scala 2.10 support) and there's no way we can
   * add a new method to the interface in a non binary breaking way.
   */
  def setIRStore(store: IRStore, cached: CachedCompiler, log: Logger): Unit = {
    cached match {
      case cached: CachedCompiler0 => cached.setIRStore(store)
      case _                       => error(log, "Fatal: compiler is not of subtype `CachedCompiler0`.")
    }
  }

  def setClassFileManager(manager: ClassFileManager, cached: CachedCompiler, log: Logger): Unit = {
    cached match {
      case cached: CachedCompiler0 => cached.setClassFileManager(manager)
      case _                       => error(log, "Fatal: compiler is not of subtype `CachedCompiler0`.")
    }
  }

  def run(
      sources: Array[File],
      changes: DependencyChanges,
      callback: AnalysisCallback,
      log: Logger,
      delegate: Reporter,
      progress: CompileProgress,
      cached: CachedCompiler
  ): Unit = cached.run(sources, changes, callback, log, delegate, progress)

  /**
   * Reset the global cache used for the classpaths created from IRs. If the
   * IRs were not created by this compiler classloader, they will be ignored.
   *
   * This method is usually called by the build tool when it knows that the
   * IRs will not be useful anymore (for example, because a compilation run
   * for a subset of the build is finished), or when there has been an error.
   *
   * The method is not added to the interface of `CompilerInterface` because
   * we still support JDK 6 (Scala 2.10 support) and there's no way we can
   * add a new method to the interface in a non binary breaking way.
   *
   * @param store The store that contains all the IRs whose caches will be removed.
   */
  def resetGlobalIRCaches(
      store: IRStore,
      cached: CachedCompiler,
      log: Logger
  ): Unit = {
    cached match {
      case cached: CachedCompiler0 => cached.resetIRCaches(store)
      case _                       => error(log, "Fatal: compiler is not of subtype `CachedCompiler0`.")
    }
  }

  def resetClassFileManager(
      manager: ClassFileManager,
      cached: CachedCompiler,
      log: Logger
  ): Unit = {
    cached match {
      case cached: CachedCompiler0 => cached.resetClassFileManager(manager)
      case _                       => error(log, "Fatal: compiler is not of subtype `CachedCompiler0`.")
    }
  }
}

class InterfaceCompileFailed(
    val arguments: Array[String],
    val problems: Array[Problem],
    override val toString: String
) extends xsbti.CompileFailed

class InterfaceCompileCancelled(val arguments: Array[String], override val toString: String)
    extends xsbti.CompileCancelled

private final class WeakLog(private[this] var log: Logger, private[this] var delegate: Reporter) {
  def apply(message: String): Unit = {
    assert(log ne null, "Stale reference to logger")
    log.error(Message(message))
  }
  def logger: Logger = log
  def reporter: Reporter = delegate
  def clear(): Unit = {
    log = null
    delegate = null
  }
}

private final class CachedCompiler0(args: Array[String], output: Output, initialLog: WeakLog)
    extends CachedCompiler
    with CachedCompilerCompat {

  /////////////////////////////////////////////////////////////////////////////////////////////////
  //////////////////////////////////// INITIALIZATION CODE ////////////////////////////////////////
  /////////////////////////////////////////////////////////////////////////////////////////////////

  val settings = new ZincSettings(s => initialLog(s))
  output match {
    case multi: MultipleOutput =>
      for (out <- multi.getOutputGroups)
        settings.outputDirs
          .add(out.getSourceDirectory.getAbsolutePath, out.getOutputDirectory.getAbsolutePath)
    case single: SingleOutput =>
      val outputFilepath = single.getOutputDirectory.getAbsolutePath
      settings.outputDirs.setSingleOutput(outputFilepath)
  }

  val command = Command(args.toList, settings)
  private[this] val dreporter = DelegatingReporter(settings, initialLog.reporter)
  try {
    if (!noErrors(dreporter)) {
      dreporter.printSummary()
      handleErrors(dreporter, initialLog.logger)
    }
  } finally initialLog.clear()

  /** Instance of the underlying Zinc compiler. */
  val compiler: ZincCompiler = {
    val settings = command.settings.asInstanceOf[ZincSettings]
    newCompiler(settings, dreporter, output)
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////

  def noErrors(dreporter: DelegatingReporter) = !dreporter.hasErrors && command.ok

  def commandArguments(sources: Array[File]): Array[String] =
    (command.settings.recreateArgs ++ sources.map(_.getAbsolutePath)).toArray[String]

  import scala.tools.nsc.Properties.versionString
  def infoOnCachedCompiler(compilerId: String): String =
    s"[zinc] Running cached compiler $compilerId for Scala compiler $versionString"

  private var store0: IRStore = null
  def setIRStore(store: IRStore): Unit = {
    store0 = store
  }

  private var manager0: ClassFileManager = null
  def setClassFileManager(manager: ClassFileManager): Unit = {
    manager0 = manager
  }

  def run(
      sources: Array[File],
      changes: DependencyChanges,
      callback: AnalysisCallback,
      log: Logger,
      reporter: Reporter,
      compileProgress: CompileProgress
  ): Unit = {
    val store = {
      if (store0 != null) store0
      else {
        warn(log, "Expected store in compiler interface! Build pipelining is misconfigured.")
        EmptyIRStore.getStore()
      }
    }

    run(sources, changes, callback, log, reporter, compileProgress, store)
  }

  def run(
      sources: Array[File],
      changes: DependencyChanges,
      callback: AnalysisCallback,
      log: Logger,
      delegate: Reporter,
      progress: CompileProgress,
      store: IRStore
  ): Unit = synchronized {
    // Set the class file manager so that we can know from where we should load symbols
    if (manager0 != null) compiler.setClassFileManager(manager0)
    // Set the IR store before forcing compiler initialization so that the new classpath is picked up
    if (!store.getDependentsIRs().isEmpty) {
      compiler.setUpIRStore(store)
    }
    debug(log, infoOnCachedCompiler(hashCode().toLong.toHexString))
    val dreporter = DelegatingReporter(settings, delegate)
    try {
      run(sources.toList, changes, callback, log, dreporter, progress)
    } finally {
      dreporter.dropDelegate()
    }
  }

  def resetIRCaches(store: IRStore): Unit = {
    compiler.clearStore(store)
  }

  def resetClassFileManager(manager: ClassFileManager): Unit = {
    manager0 = null
  }

  private def prettyPrintCompilationArguments(args: Array[String]) =
    args.mkString("[zinc] The Scala compiler is invoked with:\n\t", "\n\t", "")
  private val StopInfoError = "Compiler option supplied that disabled Zinc compilation."
  private[this] def run(
      sources: List[File],
      changes: DependencyChanges,
      callback: AnalysisCallback,
      log: Logger,
      underlyingReporter: DelegatingReporter,
      compileProgress: CompileProgress
  ): Unit = {
    if (command.shouldStopWithInfo) {
      underlyingReporter.info(null, command.getInfoMessage(compiler), true)
      throw new InterfaceCompileFailed(args, Array(), StopInfoError)
    }

    if (noErrors(underlyingReporter)) {
      debug(log, prettyPrintCompilationArguments(args))
      compiler.set(callback, underlyingReporter)
      val run = new compiler.ZincRun(compileProgress)
      val sortedSourceFiles = sources.map(_.getAbsolutePath).sortWith(_ < _)
      run.compile(sortedSourceFiles)
      processUnreportedWarnings(run)
      underlyingReporter.problems.foreach(
        p => callback.problem(p.category, p.position, p.message, p.severity, true)
      )
    }

    underlyingReporter.printSummary()
    if (!noErrors(underlyingReporter))
      handleErrors(underlyingReporter, log)

    // the case where we cancelled compilation _after_ some compilation errors got reported
    // will be handled by line above so errors still will be reported properly just potentially not
    // all of them (because we cancelled the compilation)
    if (underlyingReporter.cancelled)
      handleCompilationCancellation(underlyingReporter, log)
  }

  def handleErrors(dreporter: DelegatingReporter, log: Logger): Nothing = {
    debug(log, "Compilation failed (CompilerInterface)")
    throw new InterfaceCompileFailed(args, dreporter.problems, "Compilation failed")
  }

  def handleCompilationCancellation(dreporter: DelegatingReporter, log: Logger): Nothing = {
    assert(dreporter.cancelled, "We should get here only if when compilation got cancelled")
    debug(log, "Compilation cancelled (CompilerInterface)")
    throw new InterfaceCompileCancelled(args, "Compilation has been cancelled")
  }

  def processUnreportedWarnings(run: compiler.Run): Unit = {
    // allConditionalWarnings and the ConditionalWarning class are only in 2.10+
    final class CondWarnCompat(
        val what: String,
        val warnings: mutable.ListBuffer[(compiler.Position, String)]
    )
    implicit def compat(run: AnyRef): Compat = new Compat
    final class Compat { def allConditionalWarnings = List[CondWarnCompat]() }

    val warnings = run.allConditionalWarnings
    if (warnings.nonEmpty)
      compiler.logUnreportedWarnings(warnings.map(cw => ("" /*cw.what*/, cw.warnings.toList)))
  }
}

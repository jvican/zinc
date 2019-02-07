/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbt

import xsbti.{ AnalysisCallback, Severity }
import xsbti.compile._

import scala.tools.nsc._
import io.AbstractFile
import java.io.File

/** Defines the interface of the incremental compiler hiding implementation details. */
sealed abstract class CallbackGlobal(
    settings: Settings,
    reporter: reporters.Reporter,
    val output: Output
) extends Global(settings, reporter)
    with ZincPicklePath {

  private[this] var classFileManager: ClassFileManager = _

  def setClassFileManager(manager: ClassFileManager): Unit = {
    classFileManager = manager
  }
  def resetClassFileManager(): Unit = {
    classFileManager = null
  }

  override lazy val loaders = new {
    val global: CallbackGlobal.this.type = CallbackGlobal.this
    val platform: CallbackGlobal.this.platform.type = CallbackGlobal.this.platform
    val manager: ClassFileManager = CallbackGlobal.this.classFileManager
  } with ZincSymbolLoaders

  def foundMacroLocation: Option[String]
  def callback: AnalysisCallback
  def findAssociatedFile(name: String): Option[(AbstractFile, Boolean)]

  def fullName(
      symbol: Symbol,
      separator: Char,
      suffix: CharSequence,
      includePackageObjectClassNames: Boolean
  ): String

  lazy val outputDirs: Iterable[File] = {
    output match {
      case single: SingleOutput => List(single.getOutputDirectory)
      // Use Stream instead of List because Analyzer maps intensively over the directories
      case multi: MultipleOutput => multi.getOutputGroups.toStream map (_.getOutputDirectory)
    }
  }

  /**
   * Defines the sbt phase in which the dependency analysis is performed.
   * The reason why this is exposed in the callback global is because it's used
   * in [[xsbt.LocalToNonLocalClass]] to make sure the we don't resolve local
   * classes before we reach this phase.
   */
  private[xsbt] val sbtDependency: SubComponent

  /**
   * A map from local classes to non-local class that contains it.
   *
   * This map is used by both Dependency and Analyzer phase so it has to be
   * exposed here. The Analyzer phase uses the cached lookups performed by
   * the Dependency phase. By the time Analyzer phase is run (close to backend
   * phases), original owner chains are lost so Analyzer phase relies on
   * information saved before.
   *
   * The LocalToNonLocalClass duplicates the tracking that Scala compiler does
   * internally for backed purposes (generation of EnclosingClass attributes) but
   * that internal mapping doesn't have a stable interface we could rely on.
   */
  private[xsbt] val localToNonLocalClass = new LocalToNonLocalClass[this.type](this)
}

final class ZincSettings(errorFn: String => Unit) extends Settings(errorFn) {
  val YgeneratePickles =
    BooleanSetting("-Ygenerate-pickles", "Generate pickles for parallel or pipelined compilation.")
  val Youtline = BooleanSetting("-Youtline", "Enable type outlining.")
  val YoutlineDiff =
    BooleanSetting("-Youtline-diff", "Diff the outlined and non-outlined compilation units.")
}

/** Defines the implementation of Zinc with all its corresponding phases. */
sealed class ZincCompiler(
    override val settings: ZincSettings,
    dreporter: DelegatingReporter,
    output: Output
) extends CallbackGlobal(settings, dreporter, output)
    with ZincGlobalCompat
    with ZincOutlining {

  final class ZincRun(compileProgress: CompileProgress) extends Run {
    override def informUnitStarting(phase: Phase, unit: CompilationUnit): Unit =
      compileProgress.startUnit(phase.name, unit.source.path)
    override def progress(current: Int, total: Int): Unit =
      if (!compileProgress.advance(current, total)) cancel else ()
  }

  object dummy // temporary fix for #4426

  var foundMacroLocation: Option[String] = None
  override lazy val analyzer = new {
    val global: ZincCompiler.this.type = ZincCompiler.this
  } with typechecker.Analyzer {
    override def typedMacroBody(typer: Typer, macroDdef: DefDef): Tree = {
      // Disable pipelining if macros are defined in this project
      if (foundMacroLocation.isEmpty) foundMacroLocation = Some(macroDdef.symbol.fullLocationString)
      super.typedMacroBody(typer, macroDdef)
    }
  }

  /** Phase that analyzes the generated class files and maps them to sources. */
  object sbtAnalyzer extends {
    val global: ZincCompiler.this.type = ZincCompiler.this
    val phaseName = Analyzer.name
    val runsAfter = List("jvm")
    override val runsBefore = List("terminal")
    val runsRightAfter = None
  } with SubComponent {
    val analyzer = new Analyzer(global)
    def newPhase(prev: Phase) = analyzer.newPhase(prev)
    def name = phaseName
  }

  /** Phase that extracts dependency information */
  object sbtDependency extends {
    val global: ZincCompiler.this.type = ZincCompiler.this
    val phaseName = Dependency.name
    val runsAfter = List(API.name)
    override val runsBefore = List("refchecks")
    // Keep API and dependency close to each other -- we may want to merge them in the future.
    override val runsRightAfter = Some(API.name)
  } with SubComponent {
    val dependency = new Dependency(global)
    def newPhase(prev: Phase) = dependency.newPhase(prev)
    def name = phaseName
  }

  /**
   * Phase that walks the trees and constructs a representation of the public API.
   *
   * @note It extracts the API information after picklers to see the same symbol information
   *       irrespective of whether we typecheck from source or unpickle previously compiled classes.
   */
  object apiExtractor extends {
    val global: ZincCompiler.this.type = ZincCompiler.this
    val phaseName = API.name
    val runsAfter = List("typer")
    override val runsBefore = List("erasure")
    // TODO: Consider migrating to "uncurry" for `runsBefore`.
    // TODO: Consider removing the system property to modify which phase is used for API extraction.
    val runsRightAfter = {
      Option(System.getProperty("sbt.api.phase")).orElse {
        if (settings.YgeneratePickles.value) Some(picklerGen.phaseName)
        else Some(pickler.phaseName)
      }
    }
  } with SubComponent {
    val api = new API(global)
    def newPhase(prev: Phase) = api.newPhase(prev)
    def name = phaseName
  }

  object picklerGen extends {
    val global: ZincCompiler.this.type = ZincCompiler.this
    val phaseName = PicklerGen.name
    val runsAfter = List(pickler.phaseName)
    override val runsBefore = List(refChecks.phaseName)
    val runsRightAfter = Some(pickler.phaseName)
  } with SubComponent {
    def name: String = phaseName
    def newPhase(prev: Phase): Phase = new PicklerGen(global).newPhase(prev)
  }

  override lazy val phaseDescriptors = {
    phasesSet += sbtAnalyzer
    if (callback.enabled()) {
      phasesSet += sbtDependency
      phasesSet += apiExtractor
    }
    if (settings.YgeneratePickles.value)
      phasesSet += picklerGen
    this.computePhaseDescriptors
  }

  private final val fqnsToAssociatedFiles = perRunCaches.newMap[String, (AbstractFile, Boolean)]()

  /** Returns the associated file of a fully qualified name and whether it's on the classpath. */
  def findAssociatedFile(fqn: String): Option[(AbstractFile, Boolean)] = {
    def getOutputClass(name: String): Option[AbstractFile] = {
      // This could be improved if a hint where to look is given.
      val className = name.replace('.', '/') + ".class"
      outputDirs.map(new File(_, className)).find((_.exists)).map((AbstractFile.getFile(_)))
    }

    def findOnClassPath(name: String): Option[AbstractFile] =
      classPath.findClass(name).flatMap(_.binary.asInstanceOf[Option[AbstractFile]])

    fqnsToAssociatedFiles.get(fqn).orElse {
      val newResult = getOutputClass(fqn)
        .map(f => (f, true))
        .orElse(findOnClassPath(fqn).map(f => (f, false)))
      newResult.foreach(res => fqnsToAssociatedFiles.put(fqn, res))
      newResult
    }
  }

  /**
   * Replicate the behaviour of `fullName` with a few changes to the code to produce
   * correct file-system compatible full names for non-local classes. It mimics the
   * paths of the class files produced by genbcode.
   *
   * Changes compared to the normal version in the compiler:
   *
   * 1. It will use the encoded name instead of the normal name.
   * 2. It will not skip the name of the package object class (required for the class file path).
   *
   * Note that using `javaBinaryName` is not useful for these symbols because we
   * need the encoded names. Zinc keeps track of encoded names in both the binary
   * names and the Zinc names.
   *
   * @param symbol The symbol for which we extract the full name.
   * @param separator The separator that we will apply between every name.
   * @param suffix The suffix to add at the end (in case it's a module).
   * @param includePackageObjectClassNames Include package object class names or not.
   * @return The full name.
   */
  override def fullName(
      symbol: Symbol,
      separator: Char,
      suffix: CharSequence,
      includePackageObjectClassNames: Boolean
  ): String = {
    var b: java.lang.StringBuffer = null
    def loop(size: Int, sym: Symbol): Unit = {
      val symName = sym.name
      // Use of encoded to produce correct paths for names that have symbols
      val encodedName = symName.encoded
      val nSize = encodedName.length - (if (symName.endsWith(nme.LOCAL_SUFFIX_STRING)) 1 else 0)
      if (sym.isRoot || sym.isRootPackage || sym == NoSymbol || sym.owner.isEffectiveRoot) {
        val capacity = size + nSize
        b = new java.lang.StringBuffer(capacity)
        b.append(chrs, symName.start, nSize)
      } else {
        val next = if (sym.owner.isPackageObjectClass) sym.owner else sym.effectiveOwner.enclClass
        loop(size + nSize + 1, next)
        // Addition to normal `fullName` to produce correct names for nested non-local classes
        if (sym.isNestedClass) b.append(nme.MODULE_SUFFIX_STRING) else b.append(separator)
        b.append(chrs, symName.start, nSize)
      }
    }
    loop(suffix.length(), symbol)
    b.append(suffix)
    b.toString
  }

  private[this] var callback0: AnalysisCallback = null

  /** Returns the active analysis callback, set by [[set]] and cleared by [[clear]]. */
  def callback: AnalysisCallback = callback0

  final def set(callback: AnalysisCallback, dreporter: DelegatingReporter): Unit = {
    this.callback0 = callback
    reporter = dreporter
  }

  final def clear(): Unit = {
    callback0 = null
    superDropRun()
    reporter = null
  }

  // Scala 2.10.x and later
  private[xsbt] def logUnreportedWarnings(seq: Seq[(String, List[(Position, String)])]): Unit = {
    for ((what, warnings) <- seq; (pos, msg) <- warnings)
      yield callback.problem(what, DelegatingReporter.convert(pos), msg, Severity.Warn, false)
    ()
  }
}

import scala.reflect.internal.Positions
final class ZincCompilerRangePos(
    settings: ZincSettings,
    dreporter: DelegatingReporter,
    output: Output
) extends ZincCompiler(settings, dreporter, output)
    with Positions

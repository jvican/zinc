/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbt

import scala.tools.nsc.Phase
import scala.tools.nsc.symtab.Flags
import xsbti.api._

object API {
  val name = "xsbt-api"
}

final class API(val global: CallbackGlobal) extends Compat with GlobalHelpers with ClassName {
  import global._

  def newPhase(prev: Phase) = new ApiPhase(prev)
  class ApiPhase(prev: Phase) extends GlobalPhase(prev) {
    override def description = "Extracts the public API from source files."
    def name = API.name
    override def run(): Unit = {
      val start = System.currentTimeMillis
      super.run()
      callback.apiPhaseCompleted()
      val stop = System.currentTimeMillis
      debuglog("API phase took : " + ((stop - start) / 1000.0) + " s")
    }

    def apply(unit: global.CompilationUnit): Unit = processUnit(unit)

    private def processUnit(unit: CompilationUnit) = if (!unit.isJava) processScalaUnit(unit)

    private def processScalaUnit(unit: CompilationUnit): Unit = {
      val sourceFile = unit.source.file.file
      debuglog("Traversing " + sourceFile)
      callback.startSource(sourceFile)
      val extractApi = new ExtractAPI[global.type](global, sourceFile)
      val traverser = new TopLevelHandler(extractApi)
      traverser.apply(unit.body)

      val extractUsedNames = new ExtractUsedNames[global.type](global)
      extractUsedNames.extractAndReport(unit)

      val classApis = traverser.allNonLocalClasses
      val mainClasses = traverser.mainClasses

      // Use of iterators make this code easier to profile

      val classApisIt = classApis.iterator
      while (classApisIt.hasNext) {
        callback.api(sourceFile, classApisIt.next())
      }

      val mainClassesIt = mainClasses.iterator
      while (mainClassesIt.hasNext) {
        callback.mainClass(sourceFile, mainClassesIt.next())
      }

      // Associate class files, source files and symbols together
      registerGeneratedClasses(unit, extractApi)
    }
  }

  case class FlattenedNames(binaryName: Name, className: Name) {
    override def toString: String =
      s"binary name: ${binaryName.decode}, class name: ${className.decode}"
  }

  def flattenedNames(symbol: Symbol): FlattenedNames = {
    def toName(symbol: Symbol, x: String): Name = symbol.name.newName(x)
    val enclosingTopLevelClass = symbol.enclosingTopLevelClass
    if (enclosingTopLevelClass == symbol) {
      FlattenedNames(
        enclosingTopLevelClass.javaBinaryName,
        toName(symbol, enclosingTopLevelClass.javaClassName)
      )
    } else {
      def nextName(name: Name, symbol: Symbol): Name =
        if (symbol.isModuleClass) name else name.append(nme.MODULE_SUFFIX_STRING)

      val danglingSymbols =
        if (enclosingTopLevelClass == symbol.moduleClass) Nil
        else symbol.ownersIterator.takeWhile(s => s != enclosingTopLevelClass).toList

      val lastNameSection = danglingSymbols.reverse.foldLeft(toName(enclosingTopLevelClass, "")) {
        case (prevName: Name, nextSymbol) =>
          if (nextSymbol != symbol) prevName.append(nextName(nextSymbol.javaSimpleName, nextSymbol))
          else prevName.append(nextSymbol.javaSimpleName)
      }

      val rootBinaryName = nextName(enclosingTopLevelClass.javaBinaryName, enclosingTopLevelClass)
      val rootClassName = {
        val className = toName(enclosingTopLevelClass, enclosingTopLevelClass.javaClassName)
        nextName(className, enclosingTopLevelClass)
      }

      FlattenedNames(
        rootBinaryName.append(lastNameSection),
        rootClassName.append(lastNameSection)
      )
    }
  }

  def registerGeneratedClasses(unit: CompilationUnit, api: ExtractAPI[global.type]): Unit = {
    val sourceFile = unit.source.file
    val sourceJavaFile = sourceFile.file
    api.allExtractedNonLocalSymbols.foreach { symbol =>
      val names = flattenedNames(symbol)
      val completeClassName = s"${names.binaryName.decode}.class"
      val outputDir = global.settings.outputDirs.outputDirFor(sourceFile).file
      val classFile = new java.io.File(outputDir, completeClassName)
      if (symbol.isLocalClass) {
        callback.generatedLocalClass(sourceJavaFile, classFile)
      } else {
        val zincBinaryName = names.className.decode
        val srcClassName = classNameAsString(symbol)
        callback.generatedNonLocalClass(sourceJavaFile, classFile, zincBinaryName, srcClassName)
      }
    }
  }

  private final class TopLevelHandler(extractApi: ExtractAPI[global.type])
      extends TopLevelTraverser {
    def allNonLocalClasses: Set[ClassLike] = {
      extractApi.allExtractedNonLocalClasses
    }

    def mainClasses: Set[String] = extractApi.mainClasses

    def `class`(c: Symbol): Unit = {
      extractApi.extractAllClassesOf(c.owner, c)
    }
  }

  private abstract class TopLevelTraverser extends Traverser {
    def `class`(s: Symbol): Unit
    override def traverse(tree: Tree): Unit = {
      tree match {
        case (_: ClassDef | _: ModuleDef) if isTopLevel(tree.symbol) => `class`(tree.symbol)
        case _: PackageDef =>
          super.traverse(tree)
        case _ =>
      }
    }
    def isTopLevel(sym: Symbol): Boolean = {
      !ignoredSymbol(sym) &&
      sym.isStatic &&
      !sym.isImplClass &&
      !sym.hasFlag(Flags.SYNTHETIC) &&
      !sym.hasFlag(Flags.JAVA) &&
      !sym.isNestedClass
    }
  }

}

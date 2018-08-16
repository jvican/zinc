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

  import scala.collection.mutable
  private val allClassSymbols = new mutable.HashSet[Symbol]()

  def newPhase(prev: Phase) = new ApiPhase(prev)
  class ApiPhase(prev: Phase) extends GlobalPhase(prev) {
    override def description = "Extracts the public API from source files."
    def name = API.name
    override def run(): Unit = {
      val start = System.currentTimeMillis
      super.run()

      // After processing all units, register generated classes
      localToNonLocalClass.localClassesIterator.foreach(allClassSymbols.+=(_))
      registerGeneratedClasses(allClassSymbols.iterator)
      anonNames.clear()
      allClassSymbols.clear()

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

      extractApi.allExtractedNonLocalSymbols.foreach(allClassSymbols.+=(_))
    }
  }

  private case class FlattenedNames(binaryName: Name, className: Name) {
    override def toString: String =
      s"binary name: ${binaryName.decode}, class name: ${className.decode}"
  }

  private val anonNames: mutable.HashMap[Name, Int] = new mutable.HashMap()

  /**
   * Returns the flattened names of a symbol. The flattened names are the binary
   * name (used afterwards to reconstruct the class file where it will be compiled)
   * and the class name (used by Zinc to uniquely identify the product of a class.
   *
   * This logic works around the fact that we cannot use `javaBinaryName` before
   * `flatten` because the compiler would compute it and cache it as a side effect.
   * This side effect would perniciously leak to genbcode, which would see the wrong
   * binary name and use that to construct the class file path and avoid the
   * subscription of local classes.
   *
   * Note, too, that whenever refinement or anonymous classes are involved, we don't
   * care about using a correct index for the class in order of appearance. Zinc maps
   * the original source file to a class file path, so as long as we generate all the
   * classfile paths we're good.
   *
   * @param symbol The symbol for which we extract the names.
   * @return The flattened names, explained above.
   */
  private def flattenedNames(symbol: Symbol): FlattenedNames = {
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
        // Get owner chain before enclosing that contains no terms (i.e. which local classes have)
        else symbol.ownersIterator.takeWhile(_ != enclosingTopLevelClass).filter(!_.isTerm).toList

      val lastNameSection = danglingSymbols.reverse.foldLeft(toName(enclosingTopLevelClass, "")) {
        case (prevName: Name, nextSymbol) =>
          val symbolName = {
            // Default on the java simple name, and compute another name if class requires anon id
            val simpleName = nextSymbol.javaSimpleName
            if (!nextSymbol.isAnonOrRefinementClass) simpleName
            else {
              val anonId = anonNames.getOrElse(prevName, 1)
              anonNames.update(prevName, anonId + 1)
              simpleName.append(s"$$${anonId}")
            }
          }

          if (nextSymbol != symbol) prevName.append(nextName(symbolName, nextSymbol))
          else prevName.append(symbolName)
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

  /**
   * Registers local and non-local generated classes in the callback by extracting
   * information about its names and using the names to generate class file paths.
   *
   * Mimics the previous logic that was present in `Analyzer`, running after codegen.
   *
   * @param allClassSymbols The class symbols found in all the compilation units.
   */
  def registerGeneratedClasses(allClassSymbols: Iterator[Symbol]): Unit = {
    allClassSymbols.foreach { symbol =>
      val sourceFile = symbol.sourceFile
      val sourceJavaFile = sourceFile.file

      def registerProductNames(names: FlattenedNames): Unit = {
        val classFileName = s"${names.binaryName.decode}.class"
        val outputDir = global.settings.outputDirs.outputDirFor(sourceFile).file
        val classFile = new java.io.File(outputDir, classFileName)
        if (symbol.isLocalClass) {
          /** Local classes seem to have no relevance in the correctness of the algorithm.
           * They are used to construct the relations of products and to produce the list
           * of generated files + stamps, but names referring to local classes **never**
           * show up in the name hashes of the class API, hence being completely useless
           * for the correctness of Zinc. As local class files are owned by other classes
           * that change whenever they change, we could most likely live without adding
           * their class files to the products relation and registering their stamps.
           * I leave this for future work and more careful investigation.
           */
          callback.generatedLocalClass(sourceJavaFile, classFile)
        } else {
          val zincClassName = names.className.decode
          val srcClassName = classNameAsString(symbol)
          callback.generatedNonLocalClass(sourceJavaFile, classFile, zincClassName, srcClassName)
        }
      }

      val names = flattenedNames(symbol)
      registerProductNames(names)

      // Register the names of those module symbols that require an extra class file
      val isTopLevelUniqueModule =
        symbol.isTopLevel && symbol.isModuleClass && symbol.companionClass == NoSymbol
      if (isTopLevelUniqueModule || symbol.isPackageObject)
        registerProductNames(flattenedClassNamesFromModule(names))
    }
  }

  /**
   * Returns the flattened names for the class of an object that has no companion.
   * Required because at the class file level, two class files are created per object.
   */
  private def flattenedClassNamesFromModule(names: FlattenedNames): FlattenedNames = {
    val oldBinaryName = names.binaryName
    val newBinaryName = oldBinaryName.subName(0, oldBinaryName.length() - 1)
    val oldClassName = names.className
    val newClassName = oldClassName.subName(0, oldClassName.length() - 1)
    FlattenedNames(newBinaryName, newClassName)
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
      !sym.hasFlag(Flags.JAVA) &&
      !sym.isNestedClass
    }
  }

}

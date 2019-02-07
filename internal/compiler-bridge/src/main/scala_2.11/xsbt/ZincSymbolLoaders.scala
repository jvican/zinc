package xsbt

import xsbti.compile.ClassFileManager

import scala.tools.nsc.GlobalSymbolLoaders
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.util.ClassRepresentation

abstract class ZincSymbolLoaders extends GlobalSymbolLoaders with ZincPickleCompletion {
  import global._
  val manager: ClassFileManager

  override def initializeFromClassPath(
      owner: Symbol,
      classRep: ClassRepresentation[AbstractFile]
  ): Unit = {
    ((classRep.binary, classRep.source): @unchecked) match {
      case (Some(bin), Some(src)) if manager != null && manager.isInvalidated(bin.file) =>
        if (settings.verbose)
          inform(s"[symloader] loading from ${src.path} because Zinc invalidated ${bin.path}")
        enterToplevelsFromSource(owner, classRep.name, src)
      case (Some(bin), Some(src))
          if platform.needCompile(bin, src) && !binaryOnly(owner, classRep.name) =>
        if (settings.verbose) inform("[symloader] picked up newer source file for " + src.path)
        enterToplevelsFromSource(owner, classRep.name, src)
      case (None, Some(src)) =>
        if (settings.verbose) inform("[symloader] no class, picked up source file for " + src.path)
        enterToplevelsFromSource(owner, classRep.name, src)
      case (Some(bin), _) =>
        // If the abstract file comes from our pickle index, use our own loader
        if (bin.path.startsWith(PicklerGen.rootStartId)) {
          enterClassAndModule(owner, classRep.name, new ZincPickleLoader(bin))
        } else {
          enterClassAndModule(owner, classRep.name, new ClassfileLoader(bin))
        }
    }
  }

  final class ZincPickleLoader(val pickleFile: AbstractFile)
      extends SymbolLoader
      with FlagAssigningCompleter {

    override def description = "pickle file from " + pickleFile.toString
    override def doComplete(sym: symbolTable.Symbol): Unit = {
      val clazz = if (sym.isModule) {
        val s = sym.companionClass
        if (s == NoSymbol) sym.moduleClass else s
      } else sym
      val module = if (sym.isModule) sym else sym.companionModule
      pickleComplete(pickleFile, clazz.asClass, module.asModule, sym)
    }
  }
}

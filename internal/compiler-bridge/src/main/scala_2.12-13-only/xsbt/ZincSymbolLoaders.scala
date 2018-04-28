package xsbt

import scala.tools.nsc.GlobalSymbolLoaders
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.util.ClassRepresentation

abstract class ZincSymbolLoaders extends GlobalSymbolLoaders with ZincPickleCompletion {
  import global._

  override def initializeFromClassPath(owner: Symbol, classRep: ClassRepresentation): Unit = {
    ((classRep.binary, classRep.source): @unchecked) match {
      case (Some(bin), Some(src))
          if platform.needCompile(bin, src) && !binaryOnly(owner, classRep.name) =>
        if (settings.verbose) inform("[symloader] picked up newer source file for " + src.path)
        enterToplevelsFromSource(owner, classRep.name, src)
      case (None, Some(src)) =>
        if (settings.verbose) inform("[symloader] no class, picked up source file for " + src.path)
        enterToplevelsFromSource(owner, classRep.name, src)
      case (Some(bin), _) =>
        // If the abstract file comes from our pickle index, use our own loader
        if (bin.path.startsWith(PicklerGen.root)) {
          enterClassAndModule(owner, classRep.name, new ZincPickleLoader(bin, _, _))
        } else {
          enterClassAndModule(owner, classRep.name, new ClassfileLoader(bin, _, _))
        }
    }
  }

  final class ZincPickleLoader(
      val pickleFile: AbstractFile,
      clazz: ClassSymbol,
      module: ModuleSymbol
  ) extends SymbolLoader
      with FlagAssigningCompleter {

    override def description = "pickle file from " + pickleFile.toString

    override def doComplete(sym: symbolTable.Symbol): Unit = {
      pickleComplete(pickleFile, clazz, module, sym)
    }
  }
}

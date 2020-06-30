package xsbt

import Compat._
import java.io.File
import scala.collection.mutable

trait ZincSymbolLoaders extends GlobalSymbolLoaders with ZincPickleCompletion {
  import global._
  import scala.tools.nsc.io.AbstractFile
  import scala.tools.nsc.util.ClassRepresentation
  val invalidatedClassFilePaths: mutable.HashSet[String] = new mutable.HashSet[String]()

  private var nameCharBuffer0 = new Array[Char](256)
  override def initializeFromClassPath(owner: Symbol, classRep: ClassRepresentation): Unit = {
    def nameOf(classRep: ClassRepresentation): TermName = {
      while (true) {
        val len = classRep.nameChars(nameCharBuffer0)
        if (len == -1) nameCharBuffer0 = new Array[Char](nameCharBuffer0.length * 2)
        else return newTermName(nameCharBuffer0, 0, len)
      }
      throw new IllegalStateException()
    }

    val classRepName = nameOf(classRep)
    ((classRep.binary, classRep.source): @unchecked) match {
      case (Some(bin), Some(src))
          if platform.needCompile(bin, src) && !binaryOnly(owner, classRepName) =>
        if (settings.verbose) inform("[symloader] picked up newer source file for " + src.path)
        enterToplevelsFromSource(owner, classRepName, src)
      case (None, Some(src)) =>
        if (settings.verbose) inform("[symloader] no class, picked up source file for " + src.path)
        enterToplevelsFromSource(owner, classRepName, src)
      case (Some(bin), _) =>
        val classFile: File = bin.file
        if (classFile != null && invalidatedClassFilePaths.contains(classFile.getCanonicalPath)) {
          () // An invalidated class file should not be loaded
        } else if (bin.path.startsWith("_BPICKLE_")) {
          enterClassAndModule(owner, classRepName, new ZincPickleLoader(bin, _, _))
        } else {
          enterClassAndModule(owner, classRepName, new ClassfileLoader(bin, _, _))
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

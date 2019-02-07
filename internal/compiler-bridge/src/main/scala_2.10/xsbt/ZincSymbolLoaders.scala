package xsbt

import xsbti.compile.ClassFileManager

import scala.tools.nsc.util.{ ClassPath }
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.symtab.SymbolLoaders

// 2.10 doesn't implement pipelined compilation
abstract class ZincSymbolLoaders extends SymbolLoaders {
  import global._
  val manager: ClassFileManager

  override def initializeFromClassPath(
      owner: Symbol,
      classRep: ClassPath[platform.BinaryRepr]#ClassRep
  ): Unit = {
    ((classRep.binary, classRep.source): @unchecked) match {
      case (Some(bin: AbstractFile), Some(src))
          if bin.file != null && manager != null && manager.isInvalidated(bin.file) =>
        if (settings.verbose.value)
          inform(
            s"[symloader] picked up newer source file for ${src.path} because ${bin.file} was invalidated by Zinc"
          )
        global.loaders.enterToplevelsFromSource(owner, classRep.name, src)
      case _ => super.initializeFromClassPath(owner, classRep)
    }
  }
}

package xsbt

import Compat._
import java.io.File
import scala.collection.mutable

abstract class ZincSymbolLoaders extends GlobalSymbolLoaders {
  val invalidatedClassFilePaths: mutable.HashSet[String] = new mutable.HashSet[String]()

  import global._
  import scala.reflect.io.AbstractFile
  import scala.tools.nsc.util.{ ClassPath }
  override def initializeFromClassPath(
      owner: Symbol,
      classRep: ClassPath[platform.BinaryRepr]#ClassRep
  ) {
    ((classRep.binary, classRep.source): @unchecked) match {
      case (Some(bin), Some(src))
          if platform.needCompile(bin, src) && !binaryOnly(owner, classRep.name) =>
        if (settings.verbose.value)
          inform("[symloader] picked up newer source file for " + src.path)
        global.loaders.enterToplevelsFromSource(owner, classRep.name, src)
      case (None, Some(src)) =>
        if (settings.verbose.value)
          inform("[symloader] no class, picked up source file for " + src.path)
        global.loaders.enterToplevelsFromSource(owner, classRep.name, src)
      case (Some(bin), _) =>
        val classFilePath: String = bin match {
          case af: AbstractFile =>
            val classFile = af.file
            if (classFile == null) null
            else classFile.getCanonicalPath
          case _ => null
        }

        if (classFilePath != null && invalidatedClassFilePaths.contains(classFilePath)) {
          () // An invalidated class file should not be loaded
        } else {
          global.loaders.enterClassAndModule(owner, classRep.name, platform.newClassLoader(bin))
        }
    }
  }

}

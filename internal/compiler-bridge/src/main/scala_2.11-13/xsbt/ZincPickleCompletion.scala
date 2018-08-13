package xsbt

import scala.reflect.io.NoAbstractFile
import scala.tools.nsc.io.AbstractFile

trait ZincPickleCompletion {
  val global: CallbackGlobal
  import global._

  /** Load source or class file for `root` from Scala pickles.
   *
   * This method fills in `sym` with the information from the Scala
   * pickle. The whole mechanism takes a pickle file (which is under
   * the hood hidden as a class file so that the scala classpath
   * mechanism finds it), a class and a module symbol (representing
   * the corresponding class and module symbol related to `sym`) and
   * the symbol `sym` which is the one under completion.
   *
   * The logic here is simple: we load the pickle information from
   * the pickle file. If we're completing a module (because a companion
   * class doesn't exist), we get the information from the pickle
   * associated with the module class (which ends in `$`, assuming
   * that we did the right thing in `PicklerGen` and created it).
   *
   * As you see, for this logic to work correctly, there always need
   * to be a class file for the class and its companion. When there is
   * no class associated with a module class, we still need the pickle
   * information as if it existed because scalac requires it in some
   * cases).
   */
  def pickleComplete(
      pickleFile: AbstractFile,
      clazz: ClassSymbol,
      module: ModuleSymbol,
      sym: Symbol
  ): Unit = {
    object unpickler extends scala.reflect.internal.pickling.UnPickler {
      val symbolTable: ZincPickleCompletion.this.global.type = ZincPickleCompletion.this.global
    }

    val pickle = pickleFile.toByteArray
    if (!pickle.isEmpty) {
      val pickle = pickleFile.toByteArray
      unpickler.unpickle(pickle, 0, clazz, module, pickleFile.path)
    } else if (pickle.isEmpty && (sym.isModule || sym.isModuleClass)) {
      val moduleFileName = pickleFile.name.stripSuffix(".class") + "$.class"
      val modulePickleFile = pickleFile.container.fileNamed(moduleFileName)
      val pickle = modulePickleFile.toByteArray
      unpickler.unpickle(pickle, 0, clazz, module, pickleFile.path)
    } else {
      println(s"Error: empty pickle found in ${pickleFile.toString}, trying to recover...")
      val moduleFileName = pickleFile.name.stripSuffix(".class") + "$.class"
      val modulePickleFile = pickleFile.container.fileNamed(moduleFileName)
      val pickle = modulePickleFile.toByteArray
      unpickler.unpickle(pickle, 0, clazz, module, pickleFile.path)
    }

    // Copy pasted from ClassfileParser -- figure out if we really need it.
    if (sym.associatedFile eq NoAbstractFile) {
      sym match {
        // In fact, the ModuleSymbol forwards its setter to the module class
        case _: ClassSymbol | _: ModuleSymbol =>
          debuglog("ClassfileLoader setting %s.associatedFile = %s".format(sym.name, pickleFile))
          sym.associatedFile = pickleFile
        case _ =>
          debuglog(
            "Not setting associatedFile to %s because %s is a %s"
              .format(pickleFile, sym.name, sym.shortSymbolClass))
      }
    }
  }
}

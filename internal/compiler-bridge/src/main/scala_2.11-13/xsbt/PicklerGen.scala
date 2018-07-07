package xsbt

import java.net.URI

import scala.collection.mutable
import scala.reflect.internal.pickling.PickleBuffer
import scala.tools.nsc.Phase

final class PicklerGen(val global: CallbackGlobal) extends Compat with GlobalHelpers {
  import global._

  def newPhase(prev: Phase): Phase = new PicklerGenPhase(prev)
  private final class PicklerGenPhase(prev: Phase) extends GlobalPhase(prev) {
    override def description = "Populates in-memory pickles to be shared by Zinc instances."
    def name = PicklerGen.name
    def apply(unit: global.CompilationUnit): Unit = ()

    // Keep in mind that we need to index based on the flattened name
    override def run(): Unit = {
      val start = System.currentTimeMillis
      super.run()
      val mappings = toMappings(global.currentRun.symData)
      val handle = genPickleURI
      val rootVirtual = toVirtualFile(mappings)
      PicklerGen.urisToRoot.+=((handle, rootVirtual))
      callback.picklerPhaseCompleted(handle)
      val stop = System.currentTimeMillis
      debuglog("Picklergen phase took : " + ((stop - start) / 1000.0) + " s")
    }

    def genPickleURI: URI =
      new URI("pickle", global.hashCode().toString, global.currentRunId.toString)

    case class PickleMapping(name: String, symbol: Symbol, bytes: Array[Byte])
    def toMappings(pickles: mutable.HashMap[global.Symbol, PickleBuffer]): List[PickleMapping] = {
      pickles.toList.flatMap {
        case (symbol, pickle) =>
          val javaName = symbol.javaBinaryNameString
          val bytes = pickle.bytes.take(pickle.writeIndex)
          if (symbol.isModule) {
            if (symbol.companionClass == NoSymbol) {
              val companionJavaName = symbol.fullName('/')
              debuglog(s"Companion java name ${companionJavaName} vs name $javaName")

              /**
               * Scalac's completion engine assumes that for every module there
               * is always a companion class. This invariant is preserved in
               * `genbcode` because a companion class file is always generated.
               *
               * Here, we simulate the same behaviour. If a module has no companion
               * class, we create one that has no pickle information. We will filter
               * it out in `toIndex`, but still generate a fake class file for it
               * in `toVirtualFile`.
               */
              List(
                PickleMapping(javaName, symbol, bytes),
                PickleMapping(companionJavaName, symbol, Array())
              )
            } else List(PickleMapping(javaName, symbol, bytes))
          } else {
            List(PickleMapping(javaName, symbol, bytes))
          }
      }
    }

    /**
     * Transforms pickle mappings to in-memory virtual directories.
     *
     * @param mappings The mappings between unique names, symbols and pickle data.
     * @return The root virtual directory containing all the pickle files of this compilation unit.
     */
    def toVirtualFile(mappings: List[PickleMapping]): PickleVirtualDirectory = {
      val root = new PickleVirtualDirectory(PicklerGen.root, None)
      mappings.foreach {
        case PickleMapping(pickleName, _, bytes) =>
          pickleName.split("/", Integer.MAX_VALUE) match {
            case Array() => sys.error(s"Unexpected key format ${pickleName}.")
            case paths =>
              val parent = paths.init.foldLeft(root) {
                case (enclosingDir, dirName) =>
                  enclosingDir.subdirectoryNamed(dirName).asInstanceOf[PickleVirtualDirectory]
              }
              parent.pickleFileNamed(s"${paths.last}.class", bytes)
          }
      }
      root
    }
  }
}

object PicklerGen {
  def name = "picklergen"
  final val root = "z☎☠☣☖z"
  import scala.tools.nsc.io.VirtualDirectory
  final val urisToRoot: mutable.HashMap[URI, VirtualDirectory] = mutable.HashMap.empty
}

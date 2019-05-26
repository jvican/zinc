package xsbt

import java.net.URI

import xsbti.compile.Signature

import scala.tools.nsc.Phase
import scala.collection.mutable
import scala.reflect.internal.pickling.PickleBuffer

final class PicklerGen(val global: CallbackGlobal) extends Compat with GlobalHelpers {
  import global._

  def newPhase(prev: Phase): Phase = new PicklerGenPhase(prev)
  private final class PicklerGenPhase(prev: Phase) extends GlobalPhase(prev) {
    override def description = "Passes in-memory pickles to Zinc callback."
    def name = PicklerGen.name
    def apply(unit: global.CompilationUnit): Unit = ()

    override def run(): Unit = {
      if (!global.callback.isPipeliningEnabled) ()
      else {
        val start = System.currentTimeMillis
        super.run()
        val signatures = toSignatures(global.currentRun.symData)
        callback.definedSignatures(signatures)
        val stop = System.currentTimeMillis
        debuglog("Picklergen phase took : " + ((stop - start) / 1000.0) + " s")
      }
    }

    def toSignatures(pickles: mutable.Map[global.Symbol, PickleBuffer]): Array[Signature] = {
      val buffer = new mutable.ArrayBuffer[Signature]
      pickles.foreach {
        case (symbol, pickle) =>
          val javaName = symbol.javaBinaryNameString
          val associatedOutput = global.settings.outputDirs.outputDirFor(symbol.associatedFile).file
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
              buffer.+=(new Signature(javaName, associatedOutput, bytes))
              buffer.+=(new Signature(companionJavaName, associatedOutput, Array()))
            } else {
              buffer.+=(new Signature(javaName, associatedOutput, bytes))
            }
          } else {
            buffer.+=(new Signature(javaName, associatedOutput, bytes))
          }
      }
      buffer.toArray
    }
  }
}

object PicklerGen {
  def name = "picklergen"
  import scala.reflect.io.AbstractFile
  object PickleFile {
    import java.io.File
    def unapply(arg: AbstractFile): Option[File] = {
      arg match {
        case vf: PickleVirtualFile =>
          // The dependency comes from an in-memory ir (build pipelining is enabled)
          Some(new File(vf.sig.associatedOutput(), vf.path.stripPrefix("_BPICKLE_")))
        case _ => None
      }
    }
  }
}

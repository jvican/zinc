package xsbt

import java.net.URI

import xsbti.T2

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
      val start = System.currentTimeMillis
      super.run()
      val pickles = toPickles(global.currentRun.symData)
      callback.definedPickles(pickles)
      val stop = System.currentTimeMillis
      debuglog("Picklergen phase took : " + ((stop - start) / 1000.0) + " s")
    }

    type JavaPickleMapping = T2[String, Array[Byte]]
    case class PickleMapping(picklePath: String, bytes: Array[Byte]) extends JavaPickleMapping {
      def get1(): String = picklePath
      def get2(): Array[Byte] = bytes
    }

    def toPickles(pickles: mutable.Map[global.Symbol, PickleBuffer]): Array[JavaPickleMapping] = {
      val buffer = new mutable.ArrayBuffer[JavaPickleMapping]
      pickles.foreach {
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
              buffer.+=(PickleMapping(javaName, bytes))
              buffer.+=(PickleMapping(companionJavaName, Array()))
            } else {
              buffer.+=(PickleMapping(javaName, bytes))
            }
          } else {
            buffer.+=(PickleMapping(javaName, bytes))
          }
      }
      buffer.toArray
    }
  }
}

object PicklerGen {
  def name = "picklergen"
}

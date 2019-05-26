package xsbt

import scala.tools.nsc.Phase

final class PicklerGen(val global: CallbackGlobal) extends Compat with GlobalHelpers {
  import global._

  def newPhase(prev: Phase): Phase = new PicklerGenPhase(prev)
  private final class PicklerGenPhase(prev: Phase) extends GlobalPhase(prev) {
    override def description =
      "[disabled in 2.10] Populates in-memory pickles to be shared by Zinc instances."
    def name = PicklerGen.name
    def apply(unit: global.CompilationUnit): Unit = ()
    override def run(): Unit = ()
  }
}

object PicklerGen {
  def name = "picklergen"
  import scala.reflect.io.AbstractFile
  object PickleFile {
    import java.io.File
    def unapply(arg: AbstractFile): Option[File] = None
  }
}

package xsbt

trait ZincStatistics {
  self: ZincCompiler =>

  def reportTotal(originalTree: => Tree): Unit = ()
  def reportStatistics(normalNodes: Tree): Unit = ()
}

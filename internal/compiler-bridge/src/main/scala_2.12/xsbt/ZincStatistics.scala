package xsbt

trait ZincStatistics {
  self: ZincCompiler =>

  private val totalNodesCounter =
    new statistics.Counter("#total nodes with no outlining", List("typer"))
  private val removedOutlinedCounter =
    new statistics.SubCounter("#nodes removed by outlining", totalNodesCounter)
  private val addedOutlinedCounter =
    new statistics.SubCounter("#nodes added by outlining", removedOutlinedCounter)

  private def countNodes(t: Tree): Int = {
    var count: Int = 0
    t.foreach(_ => count += 1)
    count
  }

  def reportTotal(originalTree: => Tree): Unit = {
    if (statistics.areStatisticsLocallyEnabled) {
      statistics.incCounter(totalNodesCounter, countNodes(originalTree))
    }
  }

  def reportStatistics(normalNodes: Tree): Unit = {
    if (statistics.areStatisticsLocallyEnabled) {
      val totalCount = countNodes(normalNodes)
      statistics.incCounter(removedOutlinedCounter, totalCount)
    }
  }
}

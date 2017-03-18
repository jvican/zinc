/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbt

import scala.tools.nsc.Phase

object PositionSniffer {
  def name = "xsbt-position-sniffer"
}

/**
 * The position sniffer is the responsible of tracking tree depedencies
 * after they are lost from typer. Its named after the strategy that allow
 * us to extract this information from later phases efficiently.
 *
 * @author jvican
 */
final class PositionSniffer(val global: CallbackGlobal) {
  import global._

  def newPhase(prev: Phase): Phase = new PositionSnifferPhase(prev)
  private class PositionSnifferPhase(prev: Phase) extends GlobalPhase(prev) {
    override def description: String =
      "Keeps track of term and type dependencies that are lost after typer"
    def name: String = PositionSniffer.name
    def apply(unit: CompilationUnit): Unit = {
      if (!unit.isJava) {
        val traverser = new Sniffer
        traverser.traverse(unit.body)
      }
    }
  }

  private class Sniffer extends Traverser {
    override def traverse(tree: Tree): Unit = {
      ()
    }
  }
}

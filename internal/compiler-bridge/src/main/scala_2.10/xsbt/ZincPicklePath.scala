package scala.tools.nsc

import java.net.URI
import xsbti.compile.Signature

trait ZincPicklePath {
  self: Global =>

  def setPicklepath(signatures: Array[Signature]): Unit = ()
}

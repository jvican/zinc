package scala.tools.nsc

import java.net.URI

trait ZincPicklePath {
  self: Global =>

  def extendClassPathWithPicklePath(picklepath: List[URI]): Unit = ()
}

package scala.tools.nsc

import java.io.{ ByteArrayInputStream, InputStream }
import java.net.{ URI, URL }

import xsbt.PicklerGen

import scala.collection.mutable
import scala.reflect.io.NoAbstractFile
import scala.tools.nsc.classpath.{ AggregateClassPath, ClassPathFactory, VirtualDirectoryClassPath }
import scala.tools.nsc.io.{ AbstractFile, VirtualDirectory, VirtualFile }

trait ZincPicklePath {
  self: Global =>

  def extendClassPathWithPicklePath(picklepath: List[URI]): Unit = {
    val rootPickleDirs = picklepath.map { entry =>
      PicklerGen.urisToRoot.get(entry) match {
        case Some(dir) => dir
        case None      => sys.error(s"Invalid pickle path entry $entry. No pickle associated with it.")
      }
    }

    val pickleClassPaths = rootPickleDirs.map(d => new ZincVirtualDirectoryClassPath(d))
    val allClassPaths = pickleClassPaths ++ List(platform.classPath)
    val newClassPath = AggregateClassPath.createAggregate(allClassPaths: _*)
    platform.currentClassPath = Some(newClassPath)
  }

  // We need to override `asURLs` so that the macro classloader ignores pickle paths
  final class ZincVirtualDirectoryClassPath(dir: VirtualDirectory)
      extends VirtualDirectoryClassPath(dir) {
    override def asURLs: Seq[URL] = Nil
  }
}

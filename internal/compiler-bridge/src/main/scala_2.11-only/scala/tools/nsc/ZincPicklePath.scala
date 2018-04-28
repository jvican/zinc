package scala.tools.nsc

import java.net.{URI, URL}

import xsbt.{PicklerGen, PickleVirtualDirectory, PickleVirtualFile}

import scala.collection.immutable
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.util.ClassPath.ClassPathContext
import scala.tools.nsc.util.{DirectoryClassPath, MergedClassPath}

trait ZincPicklePath {
  self: Global =>

  def extendClassPathWithPicklePath(picklepath: List[URI]): Unit = {
    val rootPickleDirs = picklepath.map { entry =>
      PicklerGen.urisToRoot.get(entry) match {
        // We need cast because global `urisToRoot` doesn't have access to global
        case Some(dir) => dir.asInstanceOf[PickleVirtualDirectory]
        case None      => sys.error(s"Invalid pickle path entry $entry. No pickle associated with it.")
      }
    }

    val context = platform.classPath.context
    val pickleClassPaths = rootPickleDirs.map(d => new ZincVirtualDirectoryClassPath(d, context))
    val newEntries = pickleClassPaths ++: platform.classPath.entries
    val newClassPath = new MergedClassPath(newEntries, context)
    platform.currentClassPath = Some(newClassPath)
  }

  /**
    * Create our own zinc virtual directory classpath so that we can inject
    * the pickle information from them, similar to the way we do this in 2.12.
    *
    * @param dir The pickle virtual directory.
    * @param context The classpath context.
    */
  case class ZincVirtualDirectoryClassPath(
      override val dir: PickleVirtualDirectory,
      override val context: ClassPathContext[AbstractFile]
  ) extends DirectoryClassPath(dir, context) {
    override def asURLs: Seq[URL] = Nil
    override def asClassPathString = dir.path

    override lazy val (packages, classes) = {
      val classBuf = immutable.Vector.newBuilder[ClassRep]
      val packageBuf = immutable.Vector.newBuilder[DirectoryClassPath]
      dir.iterator foreach { f =>
        f match {
          case dir: PickleVirtualDirectory if validPackage(dir.name) =>
            packageBuf += new ZincVirtualDirectoryClassPath(dir, context)
          case file: PickleVirtualFile if validClassFile(file.name) =>
            classBuf += ClassRep(Some(file), None)
          case _ => ()
        }
      }
      (packageBuf.result(), classBuf.result())
    }

    override def toString() = "virtual directory classpath: " + origin.getOrElse("?")
  }
}

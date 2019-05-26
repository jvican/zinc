package scala.tools.nsc

import xsbti.compile.Signature
import xsbt.{ PickleVirtualDirectory, PickleVirtualFile, PicklerGen }

import scala.collection.immutable
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.util.ClassPath
import scala.tools.nsc.util.ClassPath.ClassPathContext
import scala.tools.nsc.util.{ DirectoryClassPath, MergedClassPath }

trait ZincPicklePath {
  self: Global =>

  private[this] var originalClassPath: ClassPath[AbstractFile] = null
  def setPicklepath(signatures: Array[Signature]): Unit = {
    val context = platform.classPath.context
    val pickleDir = toVirtualDirectory(signatures)
    val pickleClassPath = new ZincVirtualDirectoryClassPath(pickleDir, context)

    if (originalClassPath == null) {
      // Only set the original classpath the first time we initialize it
      originalClassPath = platform.classPath
    }

    val newEntries = List(pickleClassPath) ++ originalClassPath.entries
    val newClassPath = new MergedClassPath(newEntries, context)
    platform.currentClassPath = Some(newClassPath)
  }

  /**
   * Transforms IRs containing Scala signatures to in-memory virtual directories.
   *
   * This transformation is done before every compiler run.
   *
   * @param irs A sequence of Scala 2 signatures to turn into a pickle virtual directory.
   * @return The root virtual directory containing all the pickle files of this compilation unit.
   */
  def toVirtualDirectory(signatures: Array[Signature]): PickleVirtualDirectory = {
    val root = new PickleVirtualDirectory("_BPICKLE_", None)
    signatures.foreach { sig =>
      sig.nameComponents() match {
        case Array() => throw new FatalError(s"Unexpected empty path component for ${sig}.")
        case paths =>
          val parent = paths.init.foldLeft(root) {
            case (enclosingDir, dirName) =>
              enclosingDir.subdirectoryNamed(dirName).asInstanceOf[PickleVirtualDirectory]
          }
          parent.pickleFileNamed(s"${paths.last}.class", sig)
      }
    }
    root
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
    override def asURLs: Seq[java.net.URL] = Nil
    override def asClassPathString = dir.path

    override lazy val (packages, classes) = {
      val classBuf = immutable.Vector.newBuilder[ClassRep]
      val packageBuf = immutable.Vector.newBuilder[DirectoryClassPath]
      dir.children.valuesIterator.foreach { f =>
        f match {
          case dir: PickleVirtualDirectory =>
            val name = dir.name
            if ((name != "") && (name.charAt(0) != '.'))
              packageBuf += new ZincVirtualDirectoryClassPath(dir, context)
          case file: PickleVirtualFile =>
            classBuf += ClassRep(Some(file), None)
          case _ => ()
        }
      }
      (packageBuf.result(), classBuf.result())
    }

    override def toString() = "virtual directory classpath: " + origin.getOrElse("?")
  }
}

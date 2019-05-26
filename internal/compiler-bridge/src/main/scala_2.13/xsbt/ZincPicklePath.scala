package scala.tools.nsc

import xsbti.compile.Signature
import xsbt.{ PickleVirtualDirectory, PickleVirtualFile, PicklerGen }

import scala.collection.immutable
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.classpath.{ AggregateClassPath, VirtualDirectoryClassPath }
import scala.tools.nsc.io.VirtualDirectory
import scala.tools.nsc.util.ClassPath

trait ZincPicklePath {
  self: Global =>

  private[this] var originalClassPath: ClassPath = null
  def setPicklepath(signatures: Array[Signature]): Unit = {
    val pickleDir = toVirtualDirectory(signatures)
    val pickleClassPath = new ZincVirtualDirectoryClassPath(pickleDir)

    if (originalClassPath == null) {
      // Only set the original classpath the first time we initialize it
      originalClassPath = platform.classPath
    }

    val allClassPaths = pickleClassPath :: List(originalClassPath)
    val newClassPath = AggregateClassPath.createAggregate(allClassPaths: _*)
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

  // We need to override `asURLs` so that the macro classloader ignores pickle paths
  final class ZincVirtualDirectoryClassPath(dir: VirtualDirectory)
      extends VirtualDirectoryClassPath(dir) {
    override def asURLs: Seq[java.net.URL] = Nil
  }
}

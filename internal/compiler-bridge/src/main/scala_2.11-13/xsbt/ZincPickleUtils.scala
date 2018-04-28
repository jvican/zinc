package xsbt

import java.io.{ByteArrayInputStream, InputStream}

import scala.collection.mutable
import scala.reflect.io.NoAbstractFile
import scala.tools.nsc.io.{AbstractFile, VirtualDirectory, VirtualFile}

trait ZincPickleUtils {
  self: CallbackGlobal =>

  /**
   * Defines an almost exact copy of `VirtualDirectory` but allowing us to use
   * `PickleVirtualFile` for the `fileNamed` directory.
   *
   * @param name The name of the pickle directory.
   * @param maybeContainer The reference to a parent. `None` if root.
   */
  final class PickleVirtualDirectory(
      override val name: String,
      maybeContainer: Option[VirtualDirectory]
  ) extends VirtualDirectory(name, maybeContainer) {
    val children: mutable.Map[String, AbstractFile] = mutable.HashMap.empty[String, AbstractFile]
    override def clear() = children.clear()
    override def iterator = children.values.toList.iterator
    override def lookupName(name: String, directory: Boolean): AbstractFile =
      (children get name filter (_.isDirectory == directory)).orNull

    override def fileNamed(name: String): AbstractFile = {
      Option(lookupName(name, directory = false)) getOrElse {
        val newFile = new VirtualFile(name, path + '/' + name)
        children(name) = newFile
        newFile
      }
    }

    def pickleFileNamed(name: String, bytes: Array[Byte]): AbstractFile = {
      Option(lookupName(name, directory = false)) getOrElse {
        val fullPath = s"$path/$name"
        val newFile = new PickleVirtualFile(name, fullPath, bytes, Some(this))
        children(name) = newFile
        newFile
      }
    }

    override def subdirectoryNamed(name: String): AbstractFile = {
      Option(lookupName(name, directory = true)) getOrElse {
        val dir = new PickleVirtualDirectory(name, Some(this))
        children(name) = dir
        dir
      }
    }
  }

  /**
   * Simulates a `VirtualFile` but gives it a container and allows it to have content.
   *
   * @param name The name of the virtual file.
   * @param path The path of the resource.
   * @param bytes The bytes representing the pickle.
   * @param maybeContainer A parent directory. `None` if any.
   */
  final class PickleVirtualFile(
      name: String,
      path: String,
      bytes: Array[Byte],
      maybeContainer: Option[VirtualDirectory]
  ) extends VirtualFile(name, path) {
    override def input: InputStream = new ByteArrayInputStream(bytes)
    override def sizeOption: Option[Int] = Some(bytes.length)
    override def container: AbstractFile = maybeContainer.getOrElse(NoAbstractFile)
  }

}

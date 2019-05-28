/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Lightbend, Inc. and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt
package internal
package inc
package classpath

import java.io.File
import java.net.{ URI, URL, URLClassLoader }
import sbt.io.{ IO, Path, PathFinder, Using }
import xsbti.compile.ScalaInstance
import scala.util.control.NonFatal

object ClasspathUtilities {
  def toLoader(finder: PathFinder): ClassLoader = toLoader(finder, rootLoader)
  def toLoader(finder: PathFinder, parent: ClassLoader): ClassLoader =
    new URLClassLoader(finder.getURLs, parent)

  def toLoader(paths: Seq[File]): ClassLoader = toLoader(paths, rootLoader)
  def toLoader(paths: Seq[File], parent: ClassLoader): ClassLoader =
    new URLClassLoader(Path.toURLs(paths), parent)

  def toLoader(paths: Seq[File],
               parent: ClassLoader,
               resourceMap: Map[String, String]): ClassLoader =
    new URLClassLoader(Path.toURLs(paths), parent) with RawResources {
      override def resources = resourceMap
    }

  def toLoader(paths: Seq[File],
               parent: ClassLoader,
               resourceMap: Map[String, String],
               nativeTemp: File): ClassLoader =
    new URLClassLoader(Path.toURLs(paths), parent) with RawResources with NativeCopyLoader {
      override def resources = resourceMap
      override val config = new NativeCopyConfig(nativeTemp, paths, javaLibraryPaths)
      override def toString =
        s"""|URLClassLoader with NativeCopyLoader with RawResources(
            |  urls = $paths,
            |  parent = $parent,
            |  resourceMap = ${resourceMap.keySet},
            |  nativeTemp = $nativeTemp
            |)""".stripMargin
    }

  def javaLibraryPaths: Seq[File] = IO.parseClasspath(System.getProperty("java.library.path"))

  lazy val rootLoader = {
    def parent(loader: ClassLoader): ClassLoader = {
      val p = loader.getParent
      if (p eq null) loader else parent(p)
    }
    val systemLoader = ClassLoader.getSystemClassLoader
    if (systemLoader ne null) parent(systemLoader)
    else parent(getClass.getClassLoader)
  }
  lazy val xsbtiLoader = classOf[xsbti.Launcher].getClassLoader

  final val AppClassPath = "app.class.path"
  final val BootClassPath = "boot.class.path"

  def createClasspathResources(classpath: Seq[File],
                               instance: ScalaInstance): Map[String, String] = {
    createClasspathResources(classpath, Array(instance.libraryJar))
  }

  def createClasspathResources(appPaths: Seq[File], bootPaths: Seq[File]): Map[String, String] = {
    def make(name: String, paths: Seq[File]) = name -> Path.makeString(paths)
    Map(make(AppClassPath, appPaths), make(BootClassPath, bootPaths))
  }

  private[sbt] def filterByClasspath(classpath: Seq[File], loader: ClassLoader): ClassLoader =
    new ClasspathFilter(loader, xsbtiLoader, classpath.toSet)

  /**
   * Creates a ClassLoader that contains the classpath and the scala-library from
   * the given instance.
   */
  def makeLoader(classpath: Seq[File], instance: ScalaInstance): ClassLoader =
    filterByClasspath(classpath, makeLoader(classpath, instance.loaderLibraryOnly, instance))

  def makeLoader(classpath: Seq[File], instance: ScalaInstance, nativeTemp: File): ClassLoader =
    filterByClasspath(classpath,
                      makeLoader(classpath, instance.loaderLibraryOnly, instance, nativeTemp))

  def makeLoader(classpath: Seq[File], parent: ClassLoader, instance: ScalaInstance): ClassLoader =
    toLoader(classpath, parent, createClasspathResources(classpath, instance))

  def makeLoader(classpath: Seq[File],
                 parent: ClassLoader,
                 instance: ScalaInstance,
                 nativeTemp: File): ClassLoader =
    toLoader(classpath, parent, createClasspathResources(classpath, instance), nativeTemp)

  private[sbt] def printSource(c: Class[_]) =
    println(c.getName + " loader=" + c.getClassLoader + " location=" + classLocationPath(c))

  import java.nio.file.{ Path, FileSystems }
  private[sbt] val FileScheme = "file"
  private lazy val jrtFs = FileSystems.getFileSystem(URI.create("jrt:/"))

  def toFile(url: URL): File = {
    import java.net.URISyntaxException
    try { uriToFile(url.toURI) } catch { case _: URISyntaxException => new File(url.getPath) }
  }

  def urlAsFile(url: URL): Option[File] =
    url.getProtocol match {
      case FileScheme => Some(toFile(url))
      case "jar" =>
        val path = url.getPath
        val end = path.indexOf('!')
        Some(uriToFile(if (end == -1) path else path.substring(0, end)))
      case _ => None
    }

  private[this] def uriToFile(uriString: String): File = uriToFile(new URI(uriString))

  /**
   * Converts the given file URI to a File.
   */
  private[this] def uriToFile(uri: URI): File = {
    val part = uri.getSchemeSpecificPart
    // scheme might be omitted for relative URI reference.
    assert(
      Option(uri.getScheme) match {
        case None | Some(FileScheme) => true
        case _                       => false
      },
      s"Expected protocol to be '$FileScheme' or empty in URI $uri"
    )
    Option(uri.getAuthority) match {
      case None if part startsWith "/" => new File(uri)
      case _                           =>
        // https://github.com/sbt/sbt/issues/564
        // https://github.com/sbt/sbt/issues/3086
        // http://blogs.msdn.com/b/ie/archive/2006/12/06/file-uris-in-windows.aspx
        // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5086147
        // The specific problem here is that `uri` will have a defined authority component for UNC names like //foo/bar/some/path.jar
        // but the File constructor requires URIs with an undefined authority component.
        if (!(part startsWith "/") && (part contains ":")) new File("//" + part)
        else new File(part)
    }
  }

  /**
   * Returns the NIO Path to the directory, Java module, or the JAR file containing the class file `cl`.
   * If the location cannot be determined, an error is generated.
   * Note that for JDK 11 onwards, a module will return a jrt path.
   */
  def classLocationPath(cl: Class[_]): Path = {
    val u = classLocation(cl)
    val p = u.getProtocol match {
      case FileScheme => Option(toFile(u).toPath)
      case "jar"      => urlAsFile(u) map { _.toPath }
      case "jrt"      => Option(jrtFs.getPath(u.getPath))
      case _          => None
    }
    p.getOrElse(sys.error(s"Unable to create File from $u for $cl"))
  }

  /**
   * Returns the URL to the directory, Java module, or the JAR file containing the class file `cl`.
   * If the location cannot be determined or it is not a file, an error is generated.
   * Note that for JDK 11 onwards, a module will return a jrt URL such as `jrt:/java.base`.
   */
  def classLocation(cl: Class[_]): URL = {
    def localcl: Option[URL] =
      Option(cl.getProtectionDomain.getCodeSource) flatMap { codeSource =>
        Option(codeSource.getLocation)
      }
    // This assumes that classes without code sources are System classes, and thus located in jars.
    // It returns a URL that looks like jar:file:/Library/Java/JavaVirtualMachines/jdk1.8.0_131.jdk/Contents/Home/jre/lib/rt.jar!/java/lang/Integer.class
    val clsfile = s"${cl.getName.replace('.', '/')}.class"
    def syscl: Option[URL] =
      Option(ClassLoader.getSystemClassLoader) flatMap { classLoader =>
        Option(classLoader.getResource(clsfile))
      }
    try {
      localcl
        .orElse(syscl)
        .map(url =>
          url.getProtocol match {
            case "jar" =>
              val path = url.getPath
              val end = path.indexOf('!')
              new URL(
                if (end == -1) path
                else path.substring(0, end))
            case "jrt" =>
              val path = url.getPath
              val end = path.indexOf('/', 1)
              new URL("jrt",
                      null,
                      if (end == -1) path
                      else path.substring(0, end))
            case _ => url
        })
        .getOrElse(sys.error("No class location for " + cl))
    } catch {
      case NonFatal(e) =>
        e.printStackTrace()
        throw e
    }
  }

  def isArchive(file: File): Boolean = isArchive(file, contentFallback = false)

  def isArchive(file: File, contentFallback: Boolean): Boolean =
    file.isFile && (isArchiveName(file.getName) || (contentFallback && hasZipContent(file)))

  def isArchiveName(fileName: String) = fileName.endsWith(".jar") || fileName.endsWith(".zip")

  def hasZipContent(file: File): Boolean =
    try {
      Using.fileInputStream(file) { in =>
        (in.read() == 0x50) &&
        (in.read() == 0x4b) &&
        (in.read() == 0x03) &&
        (in.read() == 0x04)
      }
    } catch { case _: Exception => false }

  /** Returns all entries in 'classpath' that correspond to a compiler plugin.*/
  private[sbt] def compilerPlugins(classpath: Seq[File], isDotty: Boolean): Iterable[File] = {
    import collection.JavaConverters._
    def toURLs(files: Seq[File]): Array[URL] = files.map(_.toURI.toURL).toArray
    val loader = new URLClassLoader(toURLs(classpath))
    val metaFile = if (isDotty) "plugin.properties" else "scalac-plugin.xml"
    loader.getResources(metaFile).asScala.toList.flatMap(asFile(true))
  }

  /** Converts the given URL to a File.  If the URL is for an entry in a jar, the File for the jar is returned. */
  private[sbt] def asFile(url: URL): List[File] = asFile(false)(url)
  private[sbt] def asFile(jarOnly: Boolean)(url: URL): List[File] = {
    try {
      url.getProtocol match {
        case "file" if !jarOnly => IO.toFile(url) :: Nil
        case "jar" =>
          val path = url.getPath
          val end = path.indexOf('!')
          new File(new URI(if (end == -1) path else path.substring(0, end))) :: Nil
        case _ => Nil
      }
    } catch { case _: Exception => Nil }
  }
}

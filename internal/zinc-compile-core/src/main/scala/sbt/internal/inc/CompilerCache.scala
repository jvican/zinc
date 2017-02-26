/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import xsbti.{ Logger => xLogger, Reporter }
import xsbti.compile.{ CachedCompiler, CachedCompilerProvider, GlobalsCache, Output }
import sbt.util.Logger.f0
import java.io.File
import java.util.{ LinkedHashMap, Map }

private final class CompilerCache(val maxInstances: Int) extends GlobalsCache {
  private[this] val cache = lru[CompilerKey, CachedCompiler](maxInstances)
  private[this] def lru[A, B](max: Int) = new LinkedHashMap[A, B](8, 0.75f, true) {
    override def removeEldestEntry(eldest: Map.Entry[A, B]): Boolean = size > max
  }
  def apply(args: Array[String], output: Output, forceNew: Boolean, c: CachedCompilerProvider, log: xLogger, reporter: Reporter): CachedCompiler = synchronized {
    val key = CompilerKey(dropSources(args.toList), c.scalaInstance.actualVersion)
    if (forceNew) cache.remove(key)
    cache.get(key) match {
      case null =>
        log.debug(f0("Compiler cache miss.  " + key.toString))
        put(key, c.newCachedCompiler(args, output, log, reporter, /* resident = */ !forceNew))
      case cc =>
        log.debug(f0("Compiler cache hit (" + cc.hashCode.toLong.toHexString + ").  " + key.toString))
        cc
    }
  }
  def clear(): Unit = synchronized { cache.clear() }

  private[this] def dropSources(args: Seq[String]): Seq[String] =
    args.filterNot(arg => arg.endsWith(".scala") || arg.endsWith(".java"))

  private[this] def put(key: CompilerKey, cc: CachedCompiler): CachedCompiler =
    {
      cache.put(key, cc)
      cc
    }
  private[this] final case class CompilerKey(args: Seq[String], scalaVersion: String) {
    override def toString = "scala " + scalaVersion + ", args: " + args.mkString(" ")
  }
}
object CompilerCache {
  def apply(maxInstances: Int): GlobalsCache = new CompilerCache(maxInstances)

  val fresh: GlobalsCache = new GlobalsCache {
    def clear(): Unit = ()
    def apply(args: Array[String], output: Output, forceNew: Boolean, c: CachedCompilerProvider, log: xLogger, reporter: Reporter): CachedCompiler =
      c.newCachedCompiler(args, output, log, reporter, /*resident = */ false)
  }
}

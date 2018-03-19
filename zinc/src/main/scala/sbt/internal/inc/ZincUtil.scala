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

package sbt.internal.inc

import java.io.File
import java.net.URLClassLoader

import sbt.internal.inc.javac.JavaTools
import sbt.internal.inc.classpath.ClassLoaderCache
import xsbti.compile.{ JavaTools => XJavaTools, _ }

/** Define a private implementation of the static methods forwarded from `ZincCompilerUtil`. */
object ZincUtil {
  import xsbti.compile.ScalaInstance

  /** Return a fully-fledged, default incremental compiler ready to use. */
  def defaultIncrementalCompiler: IncrementalCompiler = new IncrementalCompilerImpl

  /**
   * Instantiate a Scala compiler that is instrumented to analyze dependencies.
   * This Scala compiler is useful to create your own instance of incremental
   * compilation.
   *
   * @see IncrementalCompiler for more details on creating your custom incremental compiler.
   *
   * @param scalaInstance The Scala instance to be used.
   * @param compilerBridgeJar The jar file or directory of the compiler bridge compiled for the
   *                          given scala instance.
   * @return A Scala compiler ready to be used.
   */
  def scalaCompiler(
      scalaInstance: ScalaInstance,
      compilerBridgeJar: File
  ): AnalyzingCompiler = {
    val emptyHandler = (_: Seq[String]) => ()
    val bridgeProvider = constantBridgeProvider(scalaInstance, compilerBridgeJar)
    val loader = Some(new ClassLoaderCache(new URLClassLoader(Array())))
    new AnalyzingCompiler(
      scalaInstance,
      bridgeProvider,
      emptyHandler,
      loader
    )
  }

  def compilers(
      instance: xsbti.compile.ScalaInstance,
      javaHome: Option[File],
      scalac: ScalaCompiler
  ): Compilers =
    compilers(JavaTools.directOrFork(instance, javaHome), scalac)

  def compilers(javaTools: XJavaTools, scalac: ScalaCompiler): Compilers = {
    Compilers.of(scalac, javaTools)
  }

  def constantBridgeProvider(
      scalaInstance: ScalaInstance,
      compilerBridgeJar: File,
  ): CompilerBridgeProvider =
    ZincCompilerUtil.constantBridgeProvider(scalaInstance, compilerBridgeJar)
}

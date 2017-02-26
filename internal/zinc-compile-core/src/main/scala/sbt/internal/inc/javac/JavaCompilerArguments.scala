/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc
package javac

import java.io.File
import CompilerArguments.{ absString, abs }

// Intended to be used with sbt.internal.inc.javac.JavaTools.
private[sbt] object JavaCompilerArguments {
  def apply(
    sources: List[File],
    classpath: List[File],
    outputDirectory: Option[File],
    options: List[String]
  ): List[String] = {
    val classpathOption = List("-classpath", absString(classpath))
    val outputOption =
      outputDirectory match {
        case Some(out) => List("-d", out.getAbsolutePath)
        case _         => Nil
      }
    options ::: outputOption ::: classpathOption ::: abs(sources)
  }
}

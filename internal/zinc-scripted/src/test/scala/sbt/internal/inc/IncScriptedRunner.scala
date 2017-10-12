/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc

import java.io.File

import sbt.internal.scripted.ScriptedRunnerImpl
import sbt.io.IO

class IncScriptedRunner {
  def run(resourceBaseDirectory: File, bufferLog: Boolean, tests: Array[String]): Unit = {
    IO.withTemporaryDirectory { tempDir =>
      // Create a global temporary directory to store the bridge et al
      val handlers = new IncScriptedHandlers(tempDir)
      ScriptedRunnerImpl.run(resourceBaseDirectory, bufferLog, tests, handlers);
    }
  }
}

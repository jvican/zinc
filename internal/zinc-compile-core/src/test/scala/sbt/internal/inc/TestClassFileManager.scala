package sbt.internal.inc

import java.io.File

import xsbti.compile.ClassFileManager

import scala.collection.mutable

/**
 * Collection of `ClassFileManager`s used for testing purposes.
 */
class CollectingClassFileManager extends ClassFileManager {

  /** Collect generated classes, with public access to allow inspection. */
  val generatedClasses = new mutable.HashSet[File]
  val deletedClasses = new mutable.HashSet[File]

  override def delete(classes: Array[File]): Unit = {
    classes.foreach(classFile => deletedClasses.add(classFile))
  }

  override def invalidatedClassFiles(): Array[File] = deletedClasses.toArray

  override def generated(classes: Array[File]): Unit = {
    generatedClasses ++= classes
    ()
  }

  override def complete(success: Boolean): Unit = ()
}

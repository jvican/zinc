/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.inc;

import xsbti.compile.MiniSetup;
import xsbti.compile.analysis.Stamp;

import java.io.File;


/**
 * Defines a generic interface to map the values of the analysis file 1-to-1.
 */
public interface GenericMapper {
    /**
     * @param sourceFile The source file to compiled.
     * @return A valid, modified source file.
     */
    File mapSourceFile(File sourceFile);

    /**
     * @param binaryFile A binary dependency of the sources to be compiled.
     * @return A valid, modified binary file.
     */
    File mapBinaryFile(File binaryFile);

    /**
     * @param productFile A product file (class file) produced by the compiler.
     * @return A valid, modified product file.
     */
    File mapProductFile(File productFile);

    /**
     * @param outputDir The output dir where the compiler will output the products.
     * @return A valid, modified output dir.
     */
    File mapOutputDir(File outputDir);

    /**
     * @param sourceDir The source dir where the compiler will look for the sources.
     * @return A valid, modified source dir.
     */
    File mapSourceDir(File sourceDir);

    /**
     * @param classpathEntry The classpath entry to be passed to the compiler.
     * @return A valid, modified classpath entry.
     */
    File mapClasspathEntry(File classpathEntry);

    /**
     * @param javacOption An option to be passed to the Java compiler.
     * @return A valid javac compiler option.
     */
    String mapJavacOption(String javacOption);

    /**
     * @param scalacOption An options to be passed to the Scala compiler.
     * @return A valid Scala compiler option.
     */
    String mapScalacOption(String scalacOption);

    /**
     * @param file The owner of the stamp.
     * @param binaryStamp A stamp associated to a binary file.
     * @return A valid, transformed stamp.
     */
    Stamp mapBinaryStamp(File file, Stamp binaryStamp);

    /**
     * @param file The owner of the stamp.
     * @param sourceStamp A stamp associated to a source file.
     * @return A valid, transformed stamp.
     */
    Stamp mapSourceStamp(File file, Stamp sourceStamp);

    /**
     * @param file The owner of the stamp.
     * @param productStamp A stamp associated to a product file.
     * @return A valid, transformed stamp.
     */
    Stamp mapProductStamp(File file, Stamp productStamp);

    /**
     * @param miniSetup The simple compile setup that is serialized in the analysis file.
     * @return A valid, transformed mini setup.
     */
    MiniSetup mapMiniSetup(MiniSetup miniSetup);
}
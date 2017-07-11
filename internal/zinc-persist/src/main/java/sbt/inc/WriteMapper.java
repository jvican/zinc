/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.inc;

import sbt.internal.inc.mappers.RelativeWriteMapper;
import xsbti.compile.MiniSetup;
import xsbti.compile.analysis.Stamp;

import java.io.File;
import java.nio.file.Path;

/**
 * Defines a writer-only mapper interface that is used by Zinc before writing
 * the contents of the analysis files to the underlying analysis store.
 *
 * This interface is useful to make the analysis file machine-independent and
 * allow third parties to distribute them around.
 */
public interface WriteMapper extends GenericMapper {

    /**
     * Defines a mapper that writes a machine-independent analysis file.
     *
     * An analysis file is machine independent if all the paths are relative and no
     * information about the machine that produced it is stored -- only information
     * about the structure of the project from a given build tool is persisted.
     *
     * The mapper makes sure that the analysis file looks like if it was generated by
     * the local machine it's executed on.
     *
     * <b>Important note</b>: The assumption that all paths can be made relative to a concrete
     * position is not always correct. There is no guarantee that the paths of the caches, the
     * artifacts, the classpath entries, the compilation inputs and outputs, et cetera share
     * the same prefix. If this is not the case this reader will fail at runtime.
     *
     * Such assumption is broken in our test infrastructure: `lib_managed` does not share
     * the same prefix, and without redefining it, it fails. Note that this is a conscious
     * design decision of the relative read and write mappers. They are focused on simplicity.
     * Build tools that need more careful handling of paths should create their own read and
     * write mappers.
     *
     * @param projectRootPath The path on which the analysis file was generated and has to be made relative.
     * @return A write mapper to pass in to {@link sbt.internal.inc.FileAnalysisStore}.
     */
    static WriteMapper getMachineIndependentMapper(Path projectRootPath) {
        return new RelativeWriteMapper(projectRootPath);
    }

    /**
     * Defines an no-op write mapper.
     *
     * This is useful when users are not interested in distributing the analysis files
     * and need to pass a read mapper to {@link sbt.internal.inc.FileAnalysisStore}.
     *
     * @return A no-op read mapper.
     */
    static WriteMapper getEmptyMapper() {
        return new WriteMapper() {
            @Override
            public File mapSourceFile(File sourceFile) {
                return sourceFile;
            }

            @Override
            public File mapBinaryFile(File binaryFile) {
                return binaryFile;
            }

            @Override
            public File mapProductFile(File productFile) {
                return productFile;
            }

            @Override
            public File mapOutputDir(File outputDir) {
                return outputDir;
            }

            @Override
            public File mapSourceDir(File sourceDir) {
                return sourceDir;
            }

            @Override
            public File mapClasspathEntry(File classpathEntry) {
                return classpathEntry;
            }

            @Override
            public String mapJavacOption(String javacOption) {
                return javacOption;
            }

            @Override
            public String mapScalacOption(String scalacOption) {
                return scalacOption;
            }

            @Override
            public Stamp mapBinaryStamp(File file, Stamp binaryStamp) {
                return binaryStamp;
            }

            @Override
            public Stamp mapSourceStamp(File file, Stamp sourceStamp) {
                return sourceStamp;
            }

            @Override
            public Stamp mapProductStamp(File file, Stamp productStamp) {
                return productStamp;
            }

            @Override
            public MiniSetup mapMiniSetup(MiniSetup miniSetup) {
                return miniSetup;
            }
        };
    }
}

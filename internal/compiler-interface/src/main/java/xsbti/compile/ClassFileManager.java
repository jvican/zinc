/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

import java.io.File;

/**
 * Represent the interface to manage the generated class files by the
 * Scala or Java compilers. The class file manager is responsible for
 * providing operations to users to allow them to have a fine-grained
 * control over the generated class files and how they are generated/deleted.
 *
 * This class is meant to be used once per compilation run.
 */
public interface ClassFileManager {
    /**
     * Handler of classes that deletes them prior to every compilation step.
     *
     * @param classes The generated class files must not exist if the method
     *                returns normally, as well as any empty ancestor
     *                directories of deleted files.
     */
    void delete(File[] classes);

    /**
     * Tells the caller what class files have been invalidated and should not
     * be used to compile the next incremental compiler run. This method is
     * necessary in case the class file manager does not delete class files in
     * the classes directory.
     * 
     * @return classes An array of all class files that were associated to invalidated symbols.
     */
    File[] invalidatedClassFiles();

    /** Called once per compilation step with the class files generated during that step. */
    /**
     * Handler of classes that decides where certain class files should be
     * stored after every compilation step.
     *
     * This method is called once per compilation run with the class
     * files generated by that concrete run.
     *
     * @param classes The generated class files by the immediate compilation run.
     */
    void generated(File[] classes);

    /** Called once at the end of the whole compilation run, with `success`
     * indicating whether compilation succeeded (true) or not (false). */
    /**
     * Informs the class file manager whether the compilation run has succeeded.
     *
     * If it has not succeeded, the class file manager will handle the current
     * generated and the previous class files as per the underlying algorithm.
     *
     * @param success Whether the compilation run has succeeded or not.
     */
    void complete(boolean success);
}

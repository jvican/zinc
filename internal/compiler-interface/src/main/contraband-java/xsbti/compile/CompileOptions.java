/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.compile;
/** Compilation options */
public final class CompileOptions implements java.io.Serializable {
    
    public static CompileOptions create() {
        return new CompileOptions();
    }
    public static CompileOptions of() {
        return new CompileOptions();
    }
    public static CompileOptions create(java.io.File[] _classpath, java.io.File[] _sources, java.io.File _classesDirectory, String[] _scalacOptions, String[] _javacOptions, int _maxErrors, java.util.function.Function<xsbti.Position, xsbti.Position> _sourcePositionMapper, xsbti.compile.CompileOrder _order) {
        return new CompileOptions(_classpath, _sources, _classesDirectory, _scalacOptions, _javacOptions, _maxErrors, _sourcePositionMapper, _order);
    }
    public static CompileOptions of(java.io.File[] _classpath, java.io.File[] _sources, java.io.File _classesDirectory, String[] _scalacOptions, String[] _javacOptions, int _maxErrors, java.util.function.Function<xsbti.Position, xsbti.Position> _sourcePositionMapper, xsbti.compile.CompileOrder _order) {
        return new CompileOptions(_classpath, _sources, _classesDirectory, _scalacOptions, _javacOptions, _maxErrors, _sourcePositionMapper, _order);
    }
    public static CompileOptions create(java.io.File[] _classpath, java.io.File[] _sources, java.io.File _classesDirectory, String[] _scalacOptions, String[] _javacOptions, xsbti.compile.ClasspathOptions _classpathOptions, int _maxErrors, java.util.function.Function<xsbti.Position, xsbti.Position> _sourcePositionMapper, xsbti.compile.CompileOrder _order) {
        return new CompileOptions(_classpath, _sources, _classesDirectory, _scalacOptions, _javacOptions, _classpathOptions, _maxErrors, _sourcePositionMapper, _order);
    }
    public static CompileOptions of(java.io.File[] _classpath, java.io.File[] _sources, java.io.File _classesDirectory, String[] _scalacOptions, String[] _javacOptions, xsbti.compile.ClasspathOptions _classpathOptions, int _maxErrors, java.util.function.Function<xsbti.Position, xsbti.Position> _sourcePositionMapper, xsbti.compile.CompileOrder _order) {
        return new CompileOptions(_classpath, _sources, _classesDirectory, _scalacOptions, _javacOptions, _classpathOptions, _maxErrors, _sourcePositionMapper, _order);
    }
    public static CompileOptions create(java.io.File[] _classpath, java.io.File[] _sources, java.io.File _classesDirectory, String[] _scalacOptions, String[] _javacOptions, xsbti.compile.ClasspathOptions _classpathOptions, int _maxErrors, java.util.function.Function<xsbti.Position, xsbti.Position> _sourcePositionMapper, xsbti.compile.CompileOrder _order, java.net.URI[] _picklepath) {
        return new CompileOptions(_classpath, _sources, _classesDirectory, _scalacOptions, _javacOptions, _classpathOptions, _maxErrors, _sourcePositionMapper, _order, _picklepath);
    }
    public static CompileOptions of(java.io.File[] _classpath, java.io.File[] _sources, java.io.File _classesDirectory, String[] _scalacOptions, String[] _javacOptions, xsbti.compile.ClasspathOptions _classpathOptions, int _maxErrors, java.util.function.Function<xsbti.Position, xsbti.Position> _sourcePositionMapper, xsbti.compile.CompileOrder _order, java.net.URI[] _picklepath) {
        return new CompileOptions(_classpath, _sources, _classesDirectory, _scalacOptions, _javacOptions, _classpathOptions, _maxErrors, _sourcePositionMapper, _order, _picklepath);
    }
    /**
     * The classpath to use for compilation.
     * This will be modified according to the ClasspathOptions used to configure the ScalaCompiler.
     */
    private java.io.File[] classpath;
    /**
     * All sources that should be recompiled.
     * This should include Scala and Java sources, which are identified by their extension.
     */
    private java.io.File[] sources;
    private java.io.File classesDirectory;
    /** The options to pass to the Scala compiler other than the sources and classpath to use. */
    private String[] scalacOptions;
    /** The options to pass to the Java compiler other than the sources and classpath to use. */
    private String[] javacOptions;
    /** The configuration to create the classpath. */
    private xsbti.compile.ClasspathOptions classpathOptions;
    private int maxErrors;
    private java.util.function.Function<xsbti.Position, xsbti.Position> sourcePositionMapper;
    /** Controls the order in which Java and Scala sources are compiled. */
    private xsbti.compile.CompileOrder order;
    /**
     * Defines the picklepath to use for compilation.
     * A pickle path is the collection of URI handles that identify in-memory
     * Scala pickles. These pickles allow Zinc to pipeline compilation of
     * multi-module builds, allowing downstream modules to start compilation
     * after the phase pickler has been executed in all the dependent modules.
     */
    private java.net.URI[] picklepath;
    protected CompileOptions() {
        super();
        classpath = new java.io.File[0];
        sources = new java.io.File[0];
        classesDirectory = new java.io.File("classes");
        scalacOptions = new String[0];
        javacOptions = new String[0];
        classpathOptions = ClasspathOptions.of(true, false, false, true, true);
        maxErrors = 100;
        sourcePositionMapper = new java.util.function.Function<xsbti.Position, xsbti.Position>() { public xsbti.Position apply(xsbti.Position a) { return a; } };
        order = xsbti.compile.CompileOrder.Mixed;
        picklepath = new java.net.URI[0];
    }
    protected CompileOptions(java.io.File[] _classpath, java.io.File[] _sources, java.io.File _classesDirectory, String[] _scalacOptions, String[] _javacOptions, int _maxErrors, java.util.function.Function<xsbti.Position, xsbti.Position> _sourcePositionMapper, xsbti.compile.CompileOrder _order) {
        super();
        classpath = _classpath;
        sources = _sources;
        classesDirectory = _classesDirectory;
        scalacOptions = _scalacOptions;
        javacOptions = _javacOptions;
        classpathOptions = ClasspathOptions.of(true, false, false, true, true);
        maxErrors = _maxErrors;
        sourcePositionMapper = _sourcePositionMapper;
        order = _order;
        picklepath = new java.net.URI[0];
    }
    protected CompileOptions(java.io.File[] _classpath, java.io.File[] _sources, java.io.File _classesDirectory, String[] _scalacOptions, String[] _javacOptions, xsbti.compile.ClasspathOptions _classpathOptions, int _maxErrors, java.util.function.Function<xsbti.Position, xsbti.Position> _sourcePositionMapper, xsbti.compile.CompileOrder _order) {
        super();
        classpath = _classpath;
        sources = _sources;
        classesDirectory = _classesDirectory;
        scalacOptions = _scalacOptions;
        javacOptions = _javacOptions;
        classpathOptions = _classpathOptions;
        maxErrors = _maxErrors;
        sourcePositionMapper = _sourcePositionMapper;
        order = _order;
        picklepath = new java.net.URI[0];
    }
    protected CompileOptions(java.io.File[] _classpath, java.io.File[] _sources, java.io.File _classesDirectory, String[] _scalacOptions, String[] _javacOptions, xsbti.compile.ClasspathOptions _classpathOptions, int _maxErrors, java.util.function.Function<xsbti.Position, xsbti.Position> _sourcePositionMapper, xsbti.compile.CompileOrder _order, java.net.URI[] _picklepath) {
        super();
        classpath = _classpath;
        sources = _sources;
        classesDirectory = _classesDirectory;
        scalacOptions = _scalacOptions;
        javacOptions = _javacOptions;
        classpathOptions = _classpathOptions;
        maxErrors = _maxErrors;
        sourcePositionMapper = _sourcePositionMapper;
        order = _order;
        picklepath = _picklepath;
    }
    public java.io.File[] classpath() {
        return this.classpath;
    }
    public java.io.File[] sources() {
        return this.sources;
    }
    public java.io.File classesDirectory() {
        return this.classesDirectory;
    }
    public String[] scalacOptions() {
        return this.scalacOptions;
    }
    public String[] javacOptions() {
        return this.javacOptions;
    }
    public xsbti.compile.ClasspathOptions classpathOptions() {
        return this.classpathOptions;
    }
    public int maxErrors() {
        return this.maxErrors;
    }
    public java.util.function.Function<xsbti.Position, xsbti.Position> sourcePositionMapper() {
        return this.sourcePositionMapper;
    }
    public xsbti.compile.CompileOrder order() {
        return this.order;
    }
    public java.net.URI[] picklepath() {
        return this.picklepath;
    }
    public CompileOptions withClasspath(java.io.File[] classpath) {
        return new CompileOptions(classpath, sources, classesDirectory, scalacOptions, javacOptions, classpathOptions, maxErrors, sourcePositionMapper, order, picklepath);
    }
    public CompileOptions withSources(java.io.File[] sources) {
        return new CompileOptions(classpath, sources, classesDirectory, scalacOptions, javacOptions, classpathOptions, maxErrors, sourcePositionMapper, order, picklepath);
    }
    public CompileOptions withClassesDirectory(java.io.File classesDirectory) {
        return new CompileOptions(classpath, sources, classesDirectory, scalacOptions, javacOptions, classpathOptions, maxErrors, sourcePositionMapper, order, picklepath);
    }
    public CompileOptions withScalacOptions(String[] scalacOptions) {
        return new CompileOptions(classpath, sources, classesDirectory, scalacOptions, javacOptions, classpathOptions, maxErrors, sourcePositionMapper, order, picklepath);
    }
    public CompileOptions withJavacOptions(String[] javacOptions) {
        return new CompileOptions(classpath, sources, classesDirectory, scalacOptions, javacOptions, classpathOptions, maxErrors, sourcePositionMapper, order, picklepath);
    }
    public CompileOptions withClasspathOptions(xsbti.compile.ClasspathOptions classpathOptions) {
        return new CompileOptions(classpath, sources, classesDirectory, scalacOptions, javacOptions, classpathOptions, maxErrors, sourcePositionMapper, order, picklepath);
    }
    public CompileOptions withMaxErrors(int maxErrors) {
        return new CompileOptions(classpath, sources, classesDirectory, scalacOptions, javacOptions, classpathOptions, maxErrors, sourcePositionMapper, order, picklepath);
    }
    public CompileOptions withSourcePositionMapper(java.util.function.Function<xsbti.Position, xsbti.Position> sourcePositionMapper) {
        return new CompileOptions(classpath, sources, classesDirectory, scalacOptions, javacOptions, classpathOptions, maxErrors, sourcePositionMapper, order, picklepath);
    }
    public CompileOptions withOrder(xsbti.compile.CompileOrder order) {
        return new CompileOptions(classpath, sources, classesDirectory, scalacOptions, javacOptions, classpathOptions, maxErrors, sourcePositionMapper, order, picklepath);
    }
    public CompileOptions withPicklepath(java.net.URI[] picklepath) {
        return new CompileOptions(classpath, sources, classesDirectory, scalacOptions, javacOptions, classpathOptions, maxErrors, sourcePositionMapper, order, picklepath);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof CompileOptions)) {
            return false;
        } else {
            CompileOptions o = (CompileOptions)obj;
            return java.util.Arrays.deepEquals(this.classpath(), o.classpath()) && java.util.Arrays.deepEquals(this.sources(), o.sources()) && this.classesDirectory().equals(o.classesDirectory()) && java.util.Arrays.deepEquals(this.scalacOptions(), o.scalacOptions()) && java.util.Arrays.deepEquals(this.javacOptions(), o.javacOptions()) && this.classpathOptions().equals(o.classpathOptions()) && (this.maxErrors() == o.maxErrors()) && this.sourcePositionMapper().equals(o.sourcePositionMapper()) && this.order().equals(o.order()) && java.util.Arrays.deepEquals(this.picklepath(), o.picklepath());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "xsbti.compile.CompileOptions".hashCode()) + java.util.Arrays.deepHashCode(classpath())) + java.util.Arrays.deepHashCode(sources())) + classesDirectory().hashCode()) + java.util.Arrays.deepHashCode(scalacOptions())) + java.util.Arrays.deepHashCode(javacOptions())) + classpathOptions().hashCode()) + (new Integer(maxErrors())).hashCode()) + sourcePositionMapper().hashCode()) + order().hashCode()) + java.util.Arrays.deepHashCode(picklepath()));
    }
    public String toString() {
        return "CompileOptions("  + "classpath: " + classpath() + ", " + "sources: " + sources() + ", " + "classesDirectory: " + classesDirectory() + ", " + "scalacOptions: " + scalacOptions() + ", " + "javacOptions: " + javacOptions() + ", " + "classpathOptions: " + classpathOptions() + ", " + "maxErrors: " + maxErrors() + ", " + "sourcePositionMapper: " + sourcePositionMapper() + ", " + "order: " + order() + ", " + "picklepath: " + picklepath() + ")";
    }
}

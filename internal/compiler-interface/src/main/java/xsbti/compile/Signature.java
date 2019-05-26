package xsbti.compile;

import java.io.File;

/**
 * Encodes a Scala signature (backed by either pickles or tasty).
 */
public class Signature {
    private String name;
    private File associatedOutput;
    private byte[] content;

    public Signature(String name, File associatedOutput, byte[] content) {
        this.name = name;
        this.associatedOutput = associatedOutput;
        this.content = content;
    }

    /**
     * Returns a Zinc fully qualified name separated by `/` associated to the
     * symbol that the given signature represents.
     */
    public String name() {
        return name;
    }

    /**
     * Returns all the name components of `name`.
     */
    public String[] nameComponents() {
        return name.split("/", java.lang.Integer.MAX_VALUE);
    }

    /**
     * Returns the associated output to a signature. The output file can refer to
     * either a JAR or a classes directory. In the presence of multiple classes
     * directory, only the one associated to the symbol that created this signature
     * is used.
     *
     * This information is required by the incremental compiler to know where a
     * given signature comes from. It's thus critical to correctly register
     * dependencies (check `processDependency` in the `Dependency` incremental
     * phase in the Scala 2 bridge for more information).
     *
     * @return The output file associated with it.
     */
    public File associatedOutput() {
        return associatedOutput;
    }

    /**
     * Returns a generic representation of a symbol signature.
     */
    public byte[] content() {
        return content;
    }
}
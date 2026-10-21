package gitbench.diff;

/**
 * A changed part of one line, in columns of the normalized text. Line and column do not depend on the line separator,
 * so they also address a document with CRLF.
 */
public record InnerRange(int line, int startColumn, int endColumn) {
}

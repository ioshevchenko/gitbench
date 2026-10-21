package gitbench.diff;

import java.nio.charset.Charset;

/**
 * The loaded text of a diff side. A binary side has no text. The line separator is the first one in the text, so an
 * edit uses the separator the file already has. The title is the column header: a short hash, "Your version", or the
 * label of an empty side.
 */
public record LoadedContent(String text, boolean binary, Charset charset, String lineSeparator, String title) {
}

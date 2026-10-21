package gitbench.merge;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes the Result of the merge window to the working file. */
public final class MergeResultWriter {
  private MergeResultWriter() {
  }

  /** {@code text} uses '\n'; the file gets {@code lineSeparator}, the separator the file had, and its charset. */
  public static void write(Path file, String text, String lineSeparator, Charset charset) throws IOException {
    Files.write(file, text.replace("\n", lineSeparator).getBytes(charset));
  }
}

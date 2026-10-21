package gitbench.merge;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes the Result of the merge window to the working file. */
public final class MergeResultWriter {
  private static final byte[] UTF8_BOM = {(byte)0xEF, (byte)0xBB, (byte)0xBF};

  private MergeResultWriter() {
  }

  /**
   * Writes the text with one line separator, the one the file had, and in its charset. A CR typed into the Result
   * becomes part of that separator, so the file never gets mixed separators. {@code bom} puts back a UTF-8 BOM.
   */
  public static void write(Path file, String text, String lineSeparator, Charset charset, boolean bom) throws IOException {
    String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    if (bom && charset.equals(StandardCharsets.UTF_8)) {
      out.write(UTF8_BOM);
    }
    out.write(normalized.replace("\n", lineSeparator).getBytes(charset));
    Files.write(file, out.toByteArray());
  }
}

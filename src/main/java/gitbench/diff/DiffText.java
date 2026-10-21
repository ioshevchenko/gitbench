package gitbench.diff;

import java.util.Arrays;

/**
 * A text with '\n' line separators only. The comparison runs on it, so CRLF on one side and LF on the other is not a
 * change, as in IntelliJ documents.
 */
public final class DiffText {
  private final String text;
  private final int[] lineStarts;

  private DiffText(String text) {
    this.text = text;
    int[] starts = new int[16];
    int count = 1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '\n') {
        if (count == starts.length) {
          starts = Arrays.copyOf(starts, count * 2);
        }
        starts[count++] = i + 1;
      }
    }
    lineStarts = Arrays.copyOf(starts, count);
  }

  public static DiffText of(String raw) {
    return new DiffText(raw.replace("\r\n", "\n").replace('\r', '\n'));
  }

  public String text() {
    return text;
  }

  /** The number of lines. A text that ends with '\n' has an empty last line, as an IntelliJ document. */
  public int lineCount() {
    return lineStarts.length;
  }

  public int lineStart(int line) {
    return lineStarts[line];
  }

  /** The offset of the line end, without the line separator. */
  public int lineEnd(int line) {
    return line + 1 < lineStarts.length ? lineStarts[line + 1] - 1 : text.length();
  }

  /** The line that holds the offset. */
  public int lineOf(int offset) {
    int index = Arrays.binarySearch(lineStarts, offset);
    return index >= 0 ? index : -index - 2;
  }
}

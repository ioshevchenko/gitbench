package gitbench.diff;

/**
 * A text change on one diff side: replace the text from (startLine, startColumn) to (endLine, endColumn) with
 * {@code text}. Positions are in the normalized text and {@code text} uses '\n', so the editor converts both to the
 * document's line separator.
 */
public record Edit(int startLine, int startColumn, int endLine, int endColumn, String text) {
  /** Applies the edit to a normalized text. */
  public String applyTo(String normalized) {
    DiffText target = DiffText.of(normalized);
    int start = target.lineStart(startLine) + startColumn;
    int end = target.lineStart(endLine) + endColumn;
    return normalized.substring(0, start) + text + normalized.substring(end);
  }
}

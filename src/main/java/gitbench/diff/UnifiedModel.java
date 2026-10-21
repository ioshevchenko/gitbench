package gitbench.diff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The one-column view of a diff, as IntelliJ UnifiedDiffViewer: context lines come from the right side, and each block
 * shows its left lines (deleted) and then its right lines (inserted).
 */
public final class UnifiedModel {
  private final String text;
  private final List<UnifiedLine> lines;
  private final int[] blockStarts;
  private final List<InnerRange> innerRanges;
  private final int[] leftToUnified;
  private final int[] rightToUnified;

  private UnifiedModel(String text, List<UnifiedLine> lines, int[] blockStarts, List<InnerRange> innerRanges,
                       int[] leftToUnified, int[] rightToUnified) {
    this.text = text;
    this.lines = lines;
    this.blockStarts = blockStarts;
    this.innerRanges = innerRanges;
    this.leftToUnified = leftToUnified;
    this.rightToUnified = rightToUnified;
  }

  public static UnifiedModel build(DiffText left, DiffText right, List<DiffChange> changes) {
    StringBuilder text = new StringBuilder();
    List<UnifiedLine> lines = new ArrayList<>();
    List<InnerRange> inner = new ArrayList<>();
    int[] blockStarts = new int[changes.size()];
    int[] leftToUnified = new int[left.lineCount()];
    int[] rightToUnified = new int[right.lineCount()];
    int leftLine = 0;
    int rightLine = 0;
    for (int i = 0; i <= changes.size(); i++) {
      DiffChange change = i < changes.size() ? changes.get(i) : null;
      int contextEnd = change != null ? change.startLine2() : right.lineCount();
      while (rightLine < contextEnd) {
        leftToUnified[leftLine] = lines.size();
        rightToUnified[rightLine] = lines.size();
        append(text, lines.size(), right, rightLine);
        lines.add(new UnifiedLine(UnifiedLine.Kind.CONTEXT, leftLine++, rightLine++, -1));
      }
      if (change == null) {
        break;
      }
      blockStarts[i] = lines.size();
      for (InnerRange range : change.inner1()) {
        inner.add(new InnerRange(lines.size() + range.line() - change.startLine1(), range.startColumn(), range.endColumn()));
      }
      for (; leftLine < change.endLine1(); leftLine++) {
        leftToUnified[leftLine] = lines.size();
        append(text, lines.size(), left, leftLine);
        lines.add(new UnifiedLine(UnifiedLine.Kind.DELETED, leftLine, -1, i));
      }
      for (InnerRange range : change.inner2()) {
        inner.add(new InnerRange(lines.size() + range.line() - change.startLine2(), range.startColumn(), range.endColumn()));
      }
      for (; rightLine < change.endLine2(); rightLine++) {
        rightToUnified[rightLine] = lines.size();
        append(text, lines.size(), right, rightLine);
        lines.add(new UnifiedLine(UnifiedLine.Kind.INSERTED, -1, rightLine, i));
      }
    }
    return new UnifiedModel(text.toString(), Collections.unmodifiableList(lines), blockStarts, Collections.unmodifiableList(inner),
                            leftToUnified, rightToUnified);
  }

  private static void append(StringBuilder text, int unifiedLine, DiffText side, int line) {
    if (unifiedLine > 0) {
      text.append('\n');
    }
    text.append(side.text(), side.lineStart(line), side.lineEnd(line));
  }

  public String text() {
    return text;
  }

  public List<UnifiedLine> lines() {
    return lines;
  }

  /** The first unified line of the block. */
  public int lineOf(int changeIndex) {
    return blockStarts[changeIndex];
  }

  /** The changed words in unified lines and columns. */
  public List<InnerRange> innerRanges() {
    return innerRanges;
  }

  public int unifiedLineOf(Side side, int line) {
    int[] map = side.select(leftToUnified, rightToUnified);
    return map[Math.max(0, Math.min(line, map.length - 1))];
  }
}

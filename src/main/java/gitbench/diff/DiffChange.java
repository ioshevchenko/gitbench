package gitbench.diff;

import java.util.List;

/**
 * One changed block: lines {@code [startLine1, endLine1)} on the left and {@code [startLine2, endLine2)} on the right.
 * The inner ranges are the changed words; they are empty when the block is highlighted by lines.
 */
public record DiffChange(int startLine1, int endLine1, int startLine2, int endLine2, BlockKind kind,
                         List<InnerRange> inner1, List<InnerRange> inner2) {
  public DiffChange {
    inner1 = List.copyOf(inner1);
    inner2 = List.copyOf(inner2);
  }

  public boolean hasInner() {
    return !inner1.isEmpty() || !inner2.isEmpty();
  }

  public int startLine(Side side) {
    return side.select(startLine1, startLine2);
  }

  public int endLine(Side side) {
    return side.select(endLine1, endLine2);
  }

  public List<InnerRange> inner(Side side) {
    return side.select(inner1, inner2);
  }
}

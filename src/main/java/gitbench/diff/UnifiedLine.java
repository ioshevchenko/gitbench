package gitbench.diff;

/**
 * One line of the unified diff. A line number is -1 when the line does not exist on that side. The change index is -1
 * for a context line.
 */
public record UnifiedLine(Kind kind, int leftLine, int rightLine, int changeIndex) {
  public enum Kind {
    CONTEXT, DELETED, INSERTED
  }
}

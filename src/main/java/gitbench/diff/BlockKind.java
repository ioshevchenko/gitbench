package gitbench.diff;

/** The type of a changed block, as IntelliJ TextDiffType: lines only on the right, only on the left, or on both. */
public enum BlockKind {
  INSERTED, DELETED, MODIFIED
}

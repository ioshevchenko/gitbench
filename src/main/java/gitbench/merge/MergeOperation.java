package gitbench.merge;

/** The git operation that stopped on conflicts. */
public enum MergeOperation {
  MERGE, REBASE, CHERRY_PICK, REVERT, NONE
}

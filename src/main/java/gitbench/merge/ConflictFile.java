package gitbench.merge;

/**
 * A file with a merge conflict, as git sees it: "ours" is stage 2 and "theirs" is stage 3. The Yours and Theirs
 * columns of the conflicts window swap them for a rebase; see {@link MergeContext#reversed()}.
 */
public record ConflictFile(String path, ConflictType type) {
  /** Stage 2 has no file. */
  public boolean oursDeleted() {
    return type == ConflictType.DELETED_BY_US || type == ConflictType.BOTH_DELETED || type == ConflictType.ADDED_BY_THEM;
  }

  /** Stage 3 has no file. */
  public boolean theirsDeleted() {
    return type == ConflictType.DELETED_BY_THEM || type == ConflictType.BOTH_DELETED || type == ConflictType.ADDED_BY_US;
  }

  /** The merge window needs the file on both sides; a deleted side is resolved with Accept Yours or Accept Theirs. */
  public boolean canMerge() {
    return !oursDeleted() && !theirsDeleted();
  }
}

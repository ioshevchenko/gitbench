package gitbench.merge;

/** The kind of a merge conflict, from the XY code of an unmerged entry in {@code git status --porcelain=v2}. */
public enum ConflictType {
  BOTH_MODIFIED, BOTH_ADDED, DELETED_BY_US, DELETED_BY_THEM, ADDED_BY_US, ADDED_BY_THEM, BOTH_DELETED;

  static ConflictType fromXy(String xy) {
    return switch (xy) {
      case "AA" -> BOTH_ADDED;
      case "DU" -> DELETED_BY_US;
      case "UD" -> DELETED_BY_THEM;
      case "AU" -> ADDED_BY_US;
      case "UA" -> ADDED_BY_THEM;
      case "DD" -> BOTH_DELETED;
      default -> BOTH_MODIFIED;
    };
  }
}

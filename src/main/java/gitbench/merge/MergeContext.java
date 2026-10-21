package gitbench.merge;

/**
 * What stopped on conflicts and the names of the two sides, for the titles of the IntelliJ conflict and merge windows
 * (GitBundle merge.dialog.*). In a rebase the sides are reversed, as in IntelliJ: Yours is the commit being applied
 * (git stage 3), Theirs is the branch it goes onto (git stage 2).
 *
 * @param currentBranch the checked-out branch, or for a rebase the branch being rebased
 * @param theirsName    the merged branch, the picked commit, or for a rebase the onto branch or short hash
 */
public record MergeContext(MergeOperation operation, String currentBranch, String theirsName) {
  public boolean reversed() {
    return operation == MergeOperation.REBASE;
  }

  /** The line above the file list of the conflicts window. */
  public String description() {
    return switch (operation) {
      case MERGE -> "Merging branch " + theirsName + " into branch " + currentBranch;
      case CHERRY_PICK -> "Conflicts during cherry-picking commit " + theirsName;
      case REVERT -> "Conflicts during reverting commit " + theirsName;
      case REBASE -> "Rebasing branch " + currentBranch + " onto " + theirsName;
      case NONE -> "";
    };
  }

  /** The title of the left column of the merge window. */
  public String yoursTitle() {
    return switch (operation) {
      case CHERRY_PICK, REVERT -> "Local Changes";
      case REBASE -> "Rebasing " + currentBranch;
      default -> "Your version, branch " + currentBranch;
    };
  }

  /** The title of the right column of the merge window. */
  public String theirsTitle() {
    return switch (operation) {
      case CHERRY_PICK -> "Changes from cherry-pick " + theirsName;
      case REVERT -> "Changes from revert " + theirsName;
      case REBASE -> "Already rebased commits and commits from " + theirsName;
      default -> "Changes from " + theirsName;
    };
  }

  /** The Yours column header of the conflicts window: "Yours", or "Yours (branch)" when the branch is known. */
  public String yoursColumn() {
    String name = reversed() ? currentBranch : operation == MergeOperation.MERGE ? currentBranch : null;
    return name == null || name.isEmpty() ? "Yours" : "Yours (" + name + ")";
  }

  public String theirsColumn() {
    return theirsName == null || theirsName.isEmpty() ? "Theirs" : "Theirs (" + theirsName + ")";
  }
}

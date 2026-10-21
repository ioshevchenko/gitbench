package gitbench.log;

/**
 * The outcome of a git operation from the log or the Branches view.
 *
 * @param conflicts  true when the operation stopped with merge conflicts
 * @param message    the text for the user; for "Undo Commit", the message of the undone commit
 * @param needsForce true when git refused a branch delete because the branch is not fully merged
 */
public record OperationResult(boolean ok, boolean conflicts, String message, boolean needsForce) {
  public OperationResult(boolean ok, boolean conflicts, String message) {
    this(ok, conflicts, message, false);
  }

  public static OperationResult success(String message) {
    return new OperationResult(true, false, message);
  }

  public static OperationResult failure(String message) {
    return new OperationResult(false, false, message);
  }
}

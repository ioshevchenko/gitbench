package gitbench.git;

/** The outcome of a push. */
public record PushResult(Status status, String message) {
  public enum Status { OK, REJECTED, UP_TO_DATE, ERROR }
}

package gitbench.git;

/** A git failure with a message that the UI can show to the user as is. */
public class GitException extends Exception {
  public GitException(String userMessage) {
    super(userMessage);
  }

  public GitException(String userMessage, Throwable cause) {
    super(userMessage, cause);
  }

  public String userMessage() {
    return getMessage();
  }

  /** Makes an exception from a failed result. Uses stderr, or stdout when stderr is empty. */
  public static GitException fromResult(String what, GitResult result) {
    String details = result.stderr().isBlank() ? result.stdoutText() : result.stderr();
    return new GitException(what + " failed: " + details.trim());
  }
}

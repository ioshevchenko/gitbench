package gitbench.log;

import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.git.GitResult;
import gitbench.git.GitRunner;

import java.util.ArrayList;
import java.util.List;

/** The operations of the commit context menu in the Git Log. Git failures become results, not exceptions. */
public final class CommitOperations {
  private final GitRunner runner;

  public CommitOperations(GitRunner runner) {
    this.runner = runner;
  }

  public OperationResult cherryPick(GitRepository repo, String hash) throws GitException {
    return run(repo, "cherry-pick", List.of("cherry-pick", hash));
  }

  public OperationResult revert(GitRepository repo, String hash) throws GitException {
    return run(repo, "revert", List.of("revert", "--no-edit", hash));
  }

  public OperationResult checkoutRevision(GitRepository repo, String hash) throws GitException {
    return run(repo, "checkout", List.of("switch", "--detach", hash));
  }

  public OperationResult reset(GitRepository repo, String hash, ResetMode mode) throws GitException {
    return run(repo, "reset", List.of("reset", mode.option(), hash));
  }

  public OperationResult createBranch(GitRepository repo, String name, String hash, boolean checkout) throws GitException {
    return createBranch(repo, name, hash, checkout, false);
  }

  /** Creates a branch at {@code hash}. With {@code overwrite}, an existing branch of that name moves there. */
  public OperationResult createBranch(GitRepository repo, String name, String hash, boolean checkout, boolean overwrite)
    throws GitException {
    if (checkout) {
      return run(repo, "branch", List.of("switch", overwrite ? "-C" : "-c", name, hash));
    }
    return run(repo, "branch", overwrite ? List.of("branch", "-f", name, hash) : List.of("branch", name, hash));
  }

  /** Creates an annotated tag, or a lightweight one when {@code messageOrNull} is null or blank. */
  public OperationResult createTag(GitRepository repo, String name, String hash, String messageOrNull) throws GitException {
    List<String> args = new ArrayList<>(List.of("tag"));
    if (messageOrNull != null && !messageOrNull.isBlank()) {
      args.addAll(List.of("-a", name, "-m", messageOrNull));
    }
    else {
      args.add(name);
    }
    args.add(hash);
    return run(repo, "tag", args);
  }

  /**
   * Undo Commit, as in IntelliJ: moves the branch one commit back and keeps the changes staged. The result message
   * is the message of the undone commit, so the Commit view can offer it again.
   */
  public OperationResult undoLastCommit(GitRepository repo) throws GitException {
    String message = runner.runOk(repo.root(), List.of("log", "-1", "--format=%B"), null).strip();
    OperationResult reset = run(repo, "undo commit", List.of("reset", "--soft", "HEAD~1"));
    return reset.ok() ? OperationResult.success(message) : reset;
  }

  public static OperationResult run(GitRunner runner, GitRepository repo, String what, List<String> args) throws GitException {
    GitResult result = runner.run(repo.root(), args, null);
    String output = (result.stdoutText() + "\n" + result.stderr()).trim();
    if (result.ok()) {
      return OperationResult.success(output);
    }
    if (output.contains("CONFLICT")) {
      return new OperationResult(false, true, "Conflicts during " + what + ". Resolve them in the Commit view.\n\n" + output);
    }
    return OperationResult.failure(output.isEmpty() ? "git " + what + " failed" : output);
  }

  private OperationResult run(GitRepository repo, String what, List<String> args) throws GitException {
    return run(runner, repo, what, args);
  }
}

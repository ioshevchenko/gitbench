package gitbench.branch;

import gitbench.git.GitException;
import gitbench.git.GitRepository;
import gitbench.git.GitRunner;
import gitbench.log.CommitOperations;
import gitbench.log.GitRef;
import gitbench.log.OperationResult;

import java.util.List;

/** The operations of the Branches view, with the behavior of the IntelliJ branch actions. */
public final class BranchOperations {
  private final GitRunner runner;

  public BranchOperations(GitRunner runner) {
    this.runner = runner;
  }

  /**
   * Checks out a branch. For a remote branch, an existing local branch of the same short name is checked out
   * instead, as in IntelliJ; otherwise a new local branch tracks the remote one. A tag gives a detached HEAD.
   */
  public OperationResult checkout(GitRepository repo, GitRef ref) throws GitException {
    return switch (ref.type()) {
      case LOCAL_BRANCH -> run(repo, "checkout", List.of("switch", ref.name()));
      case REMOTE_BRANCH -> {
        String local = ref.name().substring(ref.name().indexOf('/') + 1);
        boolean exists = runner.run(repo.root(), List.of("show-ref", "--verify", "-q", "refs/heads/" + local), null).ok();
        yield exists
              ? run(repo, "checkout", List.of("switch", local))
              : run(repo, "checkout", List.of("switch", "-c", local, "--track", ref.name()));
      }
      case TAG -> run(repo, "checkout", List.of("switch", "--detach", ref.name()));
      case HEAD -> OperationResult.success("");
    };
  }

  public OperationResult newBranch(GitRepository repo, String name, String startPoint, boolean checkout, boolean overwrite)
    throws GitException {
    return new CommitOperations(runner).createBranch(repo, name, startPoint, checkout, overwrite);
  }

  public OperationResult rename(GitRepository repo, String oldName, String newName) throws GitException {
    return run(repo, "rename", List.of("branch", "-m", oldName, newName));
  }

  /**
   * Deletes a branch. A local branch that is not fully merged is refused with {@code needsForce}; call again with
   * {@code force}. A remote branch is deleted on the remote.
   */
  public OperationResult delete(GitRepository repo, GitRef ref, boolean force) throws GitException {
    if (ref.type() == gitbench.log.RefType.REMOTE_BRANCH) {
      int slash = ref.name().indexOf('/');
      return run(repo, "delete", List.of("push", ref.name().substring(0, slash), "--delete", ref.name().substring(slash + 1)));
    }
    OperationResult result = run(repo, "delete", List.of("branch", force ? "-D" : "-d", ref.name()));
    if (!result.ok() && result.message().contains("not fully merged")) {
      return new OperationResult(false, false, "Branch '" + ref.name() + "' is not fully merged.", true);
    }
    return result;
  }

  public OperationResult mergeIntoCurrent(GitRepository repo, String ref) throws GitException {
    return run(repo, "merge", List.of("merge", "--no-edit", ref));
  }

  public OperationResult rebaseCurrentOnto(GitRepository repo, String ref) throws GitException {
    return run(repo, "rebase", List.of("rebase", ref));
  }

  /**
   * Update Selected: the current branch pulls with fast-forward only; another branch is fast-forwarded to its
   * upstream with a fetch into it.
   */
  public OperationResult updateSelected(GitRepository repo, GitRef local) throws GitException {
    if (local.current()) {
      return run(repo, "update", List.of("pull", "--ff-only"));
    }
    if (local.upstream() == null) {
      return OperationResult.failure("Branch '" + local.name() + "' has no tracked branch.");
    }
    int slash = local.upstream().indexOf('/');
    String remote = local.upstream().substring(0, slash);
    String remoteBranch = local.upstream().substring(slash + 1);
    return run(repo, "update", List.of("fetch", remote, remoteBranch + ":" + local.name()));
  }

  private OperationResult run(GitRepository repo, String what, List<String> args) throws GitException {
    return CommitOperations.run(runner, repo, what, args);
  }
}
